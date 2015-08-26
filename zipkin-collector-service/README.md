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

The Cassandra configuration file supports configuring following parameters through environment variables:

   * `CASSANDRA_USER` and `CASSANDRA_PASS`: Cassandra authentication. Will throw an exception on startup if authentication fails
   * `CASSANDRA_CONTACT_POINTS`: Comma separated list of hosts / ip addresses part of Cassandra cluster
   * `COLLECTOR_SAMPLE_RATE`: Sample rate. Double value between 0.0 (nothing ends up in back-end store) and 1.0 (everything ends up in back-end store)
   * `COLLECTOR_QUEUE_NUM_WORKERS`: Number of worker threads that pick spans from internal bounded queue and write to back-end store
   * `COLLECTOR_QUEUE_MAX_SIZE`: Internal queue size. If queue runs full offered spans are dropped. 
   * `COLLECTOR_PORT`: Collector port
   * `COLLECTOR_ADMIN_PORT`: Collector admin port. Port for admin http service. Admin service provides operational metrics for zipkin-collector-service.
   * `COLLECTOR_LOG_LEVEL`: Collector log level. Valid values: OFF, FATAL, CRITICAL, ERROR, WARNING, INFO, DEBUG, TRACE, ALL

For default values see [zipkin-collector-service/config/collector-cassandra.scala](https://github.com/openzipkin/zipkin/blob/master/zipkin-collector-service/config/collector-cassandra.scala).

Example usage:

```
CASSANDRA_USER=user CASSANDRA_PASS=pass COLLECTOR_LOG_LEVEL=ERROR ./bin/collector cassandra
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
