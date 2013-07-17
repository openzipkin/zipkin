## One-time database setup

We use the
[Anorm library](http://www.playframework.com/documentation/2.1.1/ScalaAnorm) to
support SQL databases. Anorm supports any SQL database; Zipkin includes
out-of-the-box support for SQLite, H2, MySQL, and PostgreSQL, and adding support
for additional databases is as easy as tracking down the right database driver
dependencies.

### Setting up a supported database

By default, Zipkin is configured to use a persistent SQLite database. SQLite
does not require any additional setup and the database will be created
on-demand. To use a different database, edit
`zipkin-anormdb/config/dbconfig.scala` with the relevant details. Most
databases (like MySQL and PostgreSQL) must already exist and be running.

To set up the appropriate schema, simply run these commands in the Zipkin
directory after installation:

    sbt "project zipkin-anormdb" console
    com.twitter.zipkin.storage.anormdb.DB().install

This will create new tables (prefixed by `zipkin_`) in your database.

### Adding a new database type

There are two places Zipkin needs to know about new SQL database types: in
`DB.scala` in the `zipkin-anormdb` module and in `project/Project.scala`. In
the DB class, the `private val dbmap` holds a map of database types and how to
connect to them, and in the Project file the `val anormDriverDependencies`
holds driver dependency information. Once the relevant information for your
database type is added in both places, you can continue with the schema setup
described above.

## Usage

Run these commands in order to start the Zipkin daemons:

    sbt 'project zipkin-collector-service' 'run -f zipkin-collector-service/config/collector-anorm.scala'
    sbt 'project zipkin-query-service' 'run -f zipkin-query-service/config/query-anorm.scala'
    sbt 'project zipkin-web' 'run -f zipkin-web/config/web-dev.scala -D local_docroot=zipkin-web/src/main/resources'
