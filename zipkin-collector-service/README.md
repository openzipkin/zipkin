# zipkin-collector-service

The Zipkin collector service accepts trace data via its Scribe interface.
After validity checks, the collector stores and indexes trace data into a
store, most typically SQL, Cassandra or Redis.

## Running locally

```bash
# to start a scribe listener on localhost:9410, sinking to a file-based SQL store.
./gradlew :zipkin-collector-service:run
```

#### Configuration

`zipkin-collector-service` applies configuration parameters through environment variables.

Below are links to environment variables definitions.

* Span Receivers
  * [kafka](https://github.com/openzipkin/zipkin/blob/master/zipkin-receiver-kafka/README.md)

* Span Storage
  * [dev and mysql](https://github.com/openzipkin/zipkin/blob/master/zipkin-anormdb/README.md)
  * [cassandra](https://github.com/openzipkin/zipkin/blob/master/zipkin-cassandra/README.md)
  * [redis](https://github.com/openzipkin/zipkin/blob/master/zipkin-redis/README.md)

Example usage:

```bash
$ MYSQL_USER=root ./bin/collector mysql
```

## Building and running a fat jar

```bash
./gradlew :zipkin-collector-service:build
```
This will build a fat jar `zipkin-collector-service/build/libs/zipkin-collector-service-XXX-all.jar`.

Run it, specifying a `-f` flag of the configuration file you wish to apply.

```bash
java -jar zipkin-collector-service-XXX-all.jar -f /collector-dev.scala
```

`-f` is scala configuration, and can be a local filesystem path or one of the
bundled configurations below.

* `/collector-dev.scala` - file-based SQL backend
* `/collector-cassandra.scala` - localhost cassandra backend
* `/collector-mysql.scala` - MySQL backend
* `/collector-redis.scala` - localhost redis backend
