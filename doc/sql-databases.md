This file describes how to use Zipkin with a SQL database, assuming you've
followed the other installation steps (minus setting up Cassandra).

## One-time database setup

We use the
[Anorm library](http://www.playframework.com/documentation/2.1.1/ScalaAnorm) to
support SQL databases. Anorm supports any SQL database; Zipkin includes
out-of-the-box support for SQLite, H2, MySQL, and PostgreSQL, and adding support
for additional databases is as easy as tracking down the right database driver
dependencies.

### Connecting to a database

By default, Zipkin is configured to use a persistent SQLite database. SQLite
does not require any additional setup and the database will be created
on-demand. To use a different database, edit
`zipkin-query-service/config/query-anorm.scala` and
`zipkin-collector-service/config/collector-anorm.scala` to replace this line:

    val db = DB()

With one that matches your database configuration. For example, to run Zipkin
on a MySQL database named _production_, you might write something like this:

    val db = DB(new DBConfig("mysql", new DBParams("production", "127.0.0.1")))

The connection parameters you can specify are defined in `DBConfig.scala` in
the Anorm module.

Additionally, you need to make sure Zipkin loads the correct database driver.
To do this, find the line in `project/Project.scala` that looks like this:

    anormDriverDependencies("sqlite-persistent")

Change "sqlite-persistent" to the name of the database you want to use (valid
names are specified in the definition of `anormDriverDependencies` near the top
of the file).

### Setting up the schema

To set up the appropriate schema, simply run these commands in the Zipkin
directory after installation:

    sbt "project zipkin-anormdb" console
    com.twitter.zipkin.storage.anormdb.DB().install

This will create new tables (prefixed by `zipkin_`) in your database.

### Adding a new database type

There are two places Zipkin needs to know about new SQL database types: in
`DBConfig.scala` in the `zipkin-anormdb` module and in `project/Project.scala`.
In the DBConfig class, the `private val dbmap` holds a map of database types
and how to connect to them, and in the Project file the
`val anormDriverDependencies` holds driver dependency information. Once the
relevant information for your database type is added in both places, you can
continue with the connection and schema setup processes described above.

## Usage

Run these commands in order to start the Zipkin daemons:

    sbt 'project zipkin-collector-service' 'run -f zipkin-collector-service/config/collector-anorm.scala'
    sbt 'project zipkin-query-service' 'run -f zipkin-query-service/config/query-anorm.scala'
    sbt 'project zipkin-web' 'run -f zipkin-web/config/web-dev.scala -D local_docroot=zipkin-web/src/main/resources'
