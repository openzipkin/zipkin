# zipkin-query-service

The Zipkin query service provides a thrift api over stored and indexed trace
data, most typically SQL, Cassandra or Redis.

## Running locally

```bash
# to start a query service on localhost:9411, reading from a file-based SQL store.
./gradlew :zipkin-query-service:run
```

#### Start with Cassandra Authentication

Will throw an exception on startup if authentication failed
```
# specify user/pass as environment variables
CASSANDRA_USER=user CASSANDRA_PASS=pass ./bin/query cassandra

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
* `/query-redis.scala` - localhost redis backend
