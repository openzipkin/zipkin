This file describes how to use Zipkin with a SQL database, assuming you've
already downloaded Zipkin.

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
on-demand.

To use a different database, you need to change the collector and query
configurations. The configurations are located at
`zipkin-collector-service/config/collector-dev.scala` and
`zipkin-query-service/config/query-dev.scala`, respectively. They both
instantiate a `DB`, which looks something like this:

    val db = new DB()

You need to pass in parameters to `DB()` that match your database
configuration. For example, to run Zipkin on a MySQL database named
*production*, you might write something like this:

    val db = new DB(new DBConfig("mysql", new DBParams("production", "127.0.0.1")))

The connection parameters you can specify are defined in
[`DBConfig.scala`](https://github.com/openzipkin/zipkin/blob/master/zipkin-anormdb/src/main/scala/com/openzipkin/zipkin/util/DBConfig.scala)
in the Anorm module.

Additionally, you need to make sure Zipkin loads the correct database driver.
Typically you are looking at `zipkin-collector-service/build.gradle` and `zipkin-query-service/build.gradle`.

### Setting up the schema

With the default configuration, the Zipkin collector automatically sets up the
schema if necessary. It does not add any indexes. If you are going to use
Zipkin with a SQL database in production, you may want to add indexes manually.

Zipkin knows to set up the schema through the `install` flag of a `DBConfig`
instance that is passed to the `DB` class. Once the schema has been set up, you
can turn off the flag, though the effect of keeping it on is just a slightly
slower startup.

You can also manually install the schema by running the `install()` method of
a `DB` instance.

## Usage

Zipkin uses the Anorm configuration by default, so you can run Zipkin with a
SQL database like normal:

    bin/collector
    bin/query
    bin/web
