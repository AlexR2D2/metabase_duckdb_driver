# Metabase DuckDB Driver (Community-Supported)

The Metabase DuckDB allows Metabase SNAPSHOT to use embedded DuckDB database.

This driver is community driver and is not considered part of the
core Metabase project. If you would like to open a GitHub issue to
report a bug or request new features, or would like to open a pull
requests against it, please do so in this repository, and not in the
core Metabase GitHub repository.

## DuckDB

[DuckDB](https://duckdb.org) is an in-process SQL OLAP database management. It does not run as a separate process, but completely embedded within a host process. So, it **embedds to the Metabase process** like Sqlite.

DuckDB is designed to support analytical query workloads, also known as Online analytical processing (OLAP). It contains a columnar-vectorized query execution engine, where queries are still interpreted, but a large batch of values (a “vector”) are processed in one operation. This greatly reduces overhead present in traditional systems such as PostgreSQL, MySQL or SQLite which process each row sequentially. Vectorized query execution leads to far better performance in OLAP queries. [More...](https://duckdb.org/why_duckdb)

## Obtaining the DuckDB Metabase driver

### Where to find it

[Click here](https://github.com/AlexR2D2/metabase_duckdb_driver/releases/latest) to view the latest release of the Metabase DuckDB driver; click the link to download `duckdb.metabase-driver.jar`.

You can find past releases of the DuckDB driver [here](https://github.com/AlexR2D2/metabase_duckdb_driver/releases).

### How to Install it

Metabase will automatically make the DuckDB driver available if it finds the driver in the Metabase plugins directory when it starts up.
All you need to do is create the directory `plugins` (if it's not already there), move the JAR you just downloaded into it, and restart Metabase.

By default, the plugins directory is called `plugins`, and lives in the same directory as the Metabase JAR.

For example, if you're running Metabase from a directory called `/app/`, you should move the DuckDB driver to `/app/plugins/`:

```bash
# example directory structure for running Metabase with DuckDB support
/app/metabase.jar
/app/plugins/duckdb.metabase-driver.jar
```

If you're running Metabase from the Mac App, the plugins directory defaults to `~/Library/Application Support/Metabase/Plugins/`:

```bash
# example directory structure for running Metabase Mac App with DuckDB support
/Users/you/Library/Application Support/Metabase/Plugins/duckdb.metabase-driver.jar
```

If you are running the Docker image or you want to use another directory for plugins, you should specify a custom plugins directory by setting the environment variable `MB_PLUGINS_DIR`.

## Building the DuckDB Driver Yourself

### One time setup of metabase

You require metabase to be installed alongside of your project

1. cd metabase-duckdb-driver/..
2. execute

```bash
git clone https://github.com/metabase/metabase
cd metabase
clojure -X:deps prep
cd modules/drivers
clojure -X:deps prep
cd ../../../metabase-duckdb-driver
```

### Build

1. modify :paths in deps.edn, make them absolute
2. `$`clojure -X:build :project-dir "\"$(pwd)\""`

This will build a file called `target/duckdb.metabase-driver.jar`; copy this to your Metabase `./plugins` directory.

### Build in dev container using Visual Studio Code

Install the VSCode 'Remote - Containers' extension. Start the Docker engine. Open the project in the VSCode. You will be asked if you want to re-open the project in a dev container. Reopen the project in the container. Wait until it started. Start new VSCode terminal and build plugin the same way:

1. modify :paths in deps.edn, make them absolute
2. `vscode ➜ /workspaces/metabase_duckdb_driver (main ✗) $ clojure -X:build :project-dir "\"$(pwd)\""`

## Configuring

Once you've started up Metabase, go to add a database and select "DuckDB". Provide the path to the DuckDB database file. if you don't specify a path DuckDB will be started in memory mode without any data at all.

## Parquet

Does it make sense to start DuckDB Database in memory mode without any data in system like Metabase? Of Course yes!
Because of feature of DuckDB allowing you [to run SQL queries directly on Parquet files](https://duckdb.org/2021/06/25/querying-parquet.html). So, you don't need a DuckDB database.

For example (somewhere in Metabase SQL Query editor):

```sql
# DuckDB selected as source

SELECT originalTitle, startYear, genres, numVotes, averageRating from '/Users/you/movies/title.basics.parquet' x
JOIN (SELECT * from '/Users/you/movies/title.ratings.parquet') y ON x.tconst = y.tconst
ORDER BY averageRating * numVotes DESC
```

## Docker

Unfortunately, DuckDB plugin does't work in the default Alpine based Metabase docker container due to some glibc problems. But it works in the Ubuntu based Metabase docker image. There is Ubuntu based image build script in the docker folder of this project. So, please, run Docker daemon in you host and:

```bash
./build_image.sh
```

After a while, it will build the `metabase_duckdb` Ubuntu based image of Metabase with DuckDB plugin. Just run container of this image exposing 3000 port.
