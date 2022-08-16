(ns metabase.driver.duckdb
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :as jdbc]
            [medley.core :as m]
            [metabase.driver :as driver]
            [metabase.driver.sql-jdbc.connection :as sql-jdbc.conn]
            [metabase.driver.sql-jdbc.execute :as sql-jdbc.execute]
            [metabase.driver.sql-jdbc.sync :as sql-jdbc.sync]
            [metabase.util.i18n :refer [trs]])
  (:import [java.sql ResultSet Types]))

(driver/register! :duckdb, :parent :sql-jdbc)

(defmethod sql-jdbc.conn/connection-details->spec :duckdb
  [_ {:keys [database_file], :as details}]
  (let [conn_details (merge
   {:classname   "org.duckdb.DuckDBDriver"
    :subprotocol "duckdb"
    :subname     (or database_file "")
    :read_only   true}
   (dissoc details :database_file))]
   (log/info (trs "Conn details {0}, source details {1}" (pr-str conn_details) (pr-str details)))
   conn_details))

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
    [#"DATE"        :type/Date]
    [#"TIME"        :type/Time]
    [#"TIMESTAMP"   :type/DateTime]
    [#"TIMESTAMPTZ" :type/DateTimeWithZoneOffset]
    [#"DATETIME"    :type/DateTime]]))

(defmethod sql-jdbc.sync/database-type->base-type :duckdb
  [_ field-type]
  (database-type->base-type field-type))

; .getObject of DuckDB (v0.4.0) does't handle the java.time.LocalDate but sql.Date only,
; so get the sql.Date from DuckDB and convert it to java.time.LocalDate
(defmethod sql-jdbc.execute/read-column-thunk [:duckdb Types/DATE]
  [_ ^ResultSet rs _ ^Integer i]
  (fn []
    (let [sqlDate (.getObject rs i java.sql.Date)] (.toLocalDate sqlDate))))

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
