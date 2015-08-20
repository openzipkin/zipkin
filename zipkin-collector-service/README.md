# zipkin-collector-service

The Zipkin collector service accepts trace data via its Scribe interface.
After validity checks, the collector stores and indexes trace data into a
store, most typically SQL, Cassandra or Redis.

## Running locally

```bash
# to start a scribe listener on localhost:9410, sinking to a file-based SQL store.
./gradlew :zipkin-collector-service:run
```

#### Start with Cassandra Authentication

Will throw an exception on startup if authentication failed
```
# specify user/pass as environment variables
CASSANDRA_USER=user CASSANDRA_PASS=pass ./bin/collector cassandra

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
* `/collector-redis.scala` - localhost redis backend
