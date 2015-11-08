# zipkin-query-service

The Zipkin query service provides a thrift api over stored and indexed trace
data, most typically SQL, Cassandra or Redis.

## Running locally

```bash
# to start a query service on localhost:9411, reading from a file-based SQL store.
./gradlew :zipkin-query-service:run
```

#### Configuration

`zipkin-query-service` applies configuration parameters through environment variables.

Below are environment variables definitions.

    * `QUERY_PORT`: Listen port for the query thrift api; Defaults to 9411
    * `QUERY_ADMIN_PORT`: Listen port for the ostrich admin http server; Defaults to 9901
    * `QUERY_LOG_LEVEL`: Log level written to the console; Defaults to INFO
    * `QUERY_LOOKBACK`: How many microseconds queries look back from endTs; Defaults to 7 days
    * `SCRIBE_HOST`: Listen host for scribe, where traces will be sent
    * `SCRIBE_PORT`: Listen port for scribe, where traces will be sent

* Span Storage
  * [dev and mysql](https://github.com/openzipkin/zipkin/blob/master/zipkin-anormdb/README.md)
  * [cassandra](https://github.com/openzipkin/zipkin/blob/master/zipkin-cassandra/README.md)
  * [redis](https://github.com/openzipkin/zipkin/blob/master/zipkin-redis/README.md)

Example usage:

```bash
$ CASSANDRA_USER=user CASSANDRA_PASS=pass COLLECTOR_LOG_LEVEL=ERROR ./bin/query cassandra
```

## Building and running a fat jar

```bash
./gradlew :zipkin-query-service:build
```
This will build a fat jar `zipkin-query-service/build/libs/zipkin-query-service-XXX-all.jar`.

Run it, specifying a `-f` flag of the configuration file you wish to apply.

```bash
java -jar zipkin-query-service-XXX-all.jar -f /query-dev.scala
```

`-f` is scala configuration, and can be a local filesystem path or one of the
bundled configurations below.

* `/query-dev.scala` - file-based SQL backend
* `/query-cassandra.scala` - localhost cassandra backend
* `/query-mysql.scala` - MySQL backend
* `/query-redis.scala` - localhost redis backend
