(ns metabase.driver.duckdb
  (:require [clojure.java.jdbc :as jdbc]
            [medley.core :as m]
            [metabase.driver :as driver]
            [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
            [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
            [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
            [metabase.driver.sql.query-processor :as sql.qp]
            [metabase.util.honey-sql-2 :as hx])
  (:import [java.sql Statement ResultSet ResultSetMetaData Types]))

(driver/register! :duckdb, :parent :sql-jdbc)

(defmethod sql-jdbc.conn/connection-details->spec :duckdb
  [_ {:keys [database_file, read_only], :as details}]
  (let [conn_details (merge
   {:classname         "org.duckdb.DuckDBDriver"
    :subprotocol       "duckdb"
    :subname           (or database_file "")
    "duckdb.read_only" (str read_only)}
   (dissoc details :database_file :read_only :port :engine))]
   conn_details))

(defmethod sql.qp/honey-sql-version :duckdb
           [_driver]
           2)

(def ^:private database-type->base-type
  (sql-jdbc.sync/pattern-based-database-type->base-type
   [[#"BOOLEAN"     :type/Boolean]
    [#"BOOL"        :type/Boolean]
    [#"LOGICAL"     :type/Boolean]
    [#"HUGEINT"     :type/BigInteger]
    [#"BIGINT"      :type/BigInteger]
    [#"UBIGINT"     :type/BigInteger]
    [#"INT8"        :type/BigInteger]
    [#"LONG"        :type/BigInteger]
    [#"INT"         :type/Integer]
    [#"INTEGER"     :type/Integer]
    [#"INT4"        :type/Integer]
    [#"SIGNED"      :type/Integer]
    [#"SMALLINT"    :type/Integer]
    [#"INT2"        :type/Integer]
    [#"SHORT"       :type/Integer]
    [#"TINYINT"     :type/Integer]
    [#"INT1"        :type/Integer]
    [#"UINTEGER"    :type/Integer]
    [#"USMALLINT"   :type/Integer]
    [#"UTINYINT"    :type/Integer]
    [#"DECIMAL"     :type/Decimal]
    [#"DOUBLE"      :type/Float]
    [#"FLOAT8"      :type/Float]
    [#"NUMERIC"     :type/Float]
    [#"REAL"        :type/Float]
    [#"FLOAT4"      :type/Float]
    [#"FLOAT"       :type/Float]
    [#"VARCHAR"     :type/Text]
    [#"CHAR"        :type/Text]
    [#"BPCHAR"      :type/Text]
    [#"TEXT"        :type/Text]
    [#"STRING"      :type/Text]
    [#"BLOB"        :type/*]
    [#"BYTEA"       :type/*]
    [#"BINARY"      :type/*]
    [#"VARBINARY"   :type/*]
    [#"UUID"        :type/UUID]
    [#"TIMESTAMP"   :type/DateTime]
    [#"DATETIME"    :type/DateTime]
    [#"TIMESTAMPTZ" :type/DateTimeWithZoneOffset]
    [#"DATE"        :type/Date]
    [#"TIME"        :type/Time]]))

(defmethod sql-jdbc.sync/database-type->base-type :duckdb
  [_ field-type]
  (database-type->base-type field-type))

;; .getObject of DuckDB (v0.4.0) does't handle the java.time.LocalDate but sql.Date only,
;; so get the sql.Date from DuckDB and convert it to java.time.LocalDate
(defmethod sql-jdbc.execute/read-column-thunk [:duckdb Types/DATE]
  [_ ^ResultSet rs _rsmeta ^Integer i]
  (fn []
    (when-let [sqlDate (.getDate rs i)]
      (.toLocalDate sqlDate))))

;; .getObject of DuckDB (v0.4.0) does't handle the java.time.LocalTime but sql.Time only,
;; so get the sql.Time from DuckDB and convert it to java.time.LocalTime
(defmethod sql-jdbc.execute/read-column-thunk [:duckdb Types/TIME]
  [_ ^ResultSet rs _rsmeta ^Integer i]
  (fn []
    (when-let [sqlTime (.getTime rs i)]
      (.toLocalTime sqlTime))))

;; date processing for aggregation

(defmethod driver/db-start-of-week :duckdb [_] :monday)

(defmethod sql.qp/add-interval-honeysql-form :duckdb
  [driver hsql-form amount unit]
  (if (= unit :quarter)
    (recur driver hsql-form (* amount 3) :month)
    (hx/+ (hx/->timestamp hsql-form) [:raw (format "(INTERVAL '%d' %s)" (int amount) (name unit))])))

(defmethod sql.qp/date [:duckdb :default]         [_ _ expr] expr)
(defmethod sql.qp/date [:duckdb :minute]          [_ _ expr] [:date_trunc (hx/literal :minute) expr])
(defmethod sql.qp/date [:duckdb :minute-of-hour]  [_ _ expr] [:minute expr])
(defmethod sql.qp/date [:duckdb :hour]            [_ _ expr] [:date_trunc (hx/literal :hour) expr])
(defmethod sql.qp/date [:duckdb :hour-of-day]     [_ _ expr] [:hour expr])
(defmethod sql.qp/date [:duckdb :day]             [_ _ expr] [:date_trunc (hx/literal :day) expr])
(defmethod sql.qp/date [:duckdb :day-of-month]    [_ _ expr] [:day expr])
(defmethod sql.qp/date [:duckdb :day-of-year]     [_ _ expr] [:dayofyear expr])

(defmethod sql.qp/date [:duckdb :day-of-week]
  [_ _ expr]
  (sql.qp/adjust-day-of-week :duckdb [:dayofweek expr]))

(defmethod sql.qp/date [:duckdb :week]
  [_ _ expr]
  (sql.qp/adjust-start-of-week :duckdb (partial conj [:date_trunc] (hx/literal :week)) expr))

(defmethod sql.qp/date [:duckdb :month]           [_ _ expr] [:date_trunc (hx/literal :month) expr])
(defmethod sql.qp/date [:duckdb :month-of-year]   [_ _ expr] [:month expr])
(defmethod sql.qp/date [:duckdb :quarter]         [_ _ expr] [:date_trunc (hx/literal :quarter) expr])
(defmethod sql.qp/date [:duckdb :quarter-of-year] [_ _ expr] [:quarter expr])
(defmethod sql.qp/date [:duckdb :year]            [_ _ expr] [:date_trunc (hx/literal :year) expr])

(defmethod sql.qp/unix-timestamp->honeysql [:duckdb :seconds]
  [_ _ expr]
  [:from_unixtime expr])

;; emty result set for queries without result (like insert...)

(defn empty-rs [_] ;
  (reify
    ResultSet
    (getMetaData [_]
      (reify
        ResultSetMetaData
        (getColumnCount [_] 1)
        (getColumnLabel [_ _idx] "WARNING")
        (getColumnTypeName [_ _] "CHAR")
        (getColumnType [_ _] Types/CHAR)))
    (next [_] false)
    (close [_])))

;; override native execute-statement! to make queries that does't returns ResultSet

(defmethod sql-jdbc.execute/execute-statement! :sql-jdbc
  [_driver ^Statement stmt ^String sql]
  (if (.execute stmt sql)
    (.getResultSet stmt)
    (empty-rs [])))

(defmethod driver/describe-database :duckdb
  [_ database]
  {:tables
    (with-open [conn (jdbc/get-connection (sql-jdbc.conn/db->pooled-connection-spec database))]
      (set
        (for [
          {:keys [table_schema table_name]}
          (jdbc/query {:connection conn}
          ["select * from information_schema.tables"])
        ]
          {:name table_name :schema table_schema})))})

(defmethod driver/describe-table :duckdb
  [_ database {table_name :name, schema :schema}]
  {:name   table_name
   :schema schema
   :fields
   (with-open [conn (jdbc/get-connection (sql-jdbc.conn/db->pooled-connection-spec database))]
     (let [results (jdbc/query
                    {:connection conn}
                    [(format "select * from information_schema.columns where table_name = '%s'" table_name)])]
       (set
        (for [[idx {column_name :column_name, data_type :data_type}] (m/indexed results)]
          {:name              column_name
           :database-type     data_type
           :base-type         (sql-jdbc.sync/database-type->base-type :duckdb (keyword data_type))
           :database-position idx}))))})

;; The 0.4.0 DuckDB JDBC .getImportedKeys method throws 'not implemented' yet.
;; There is no support of FK yet.
(defmethod driver/describe-table-fks :duckdb
  [_ _ _]
  (set #{}))
