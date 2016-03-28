# zipkin-server
zipkin-server is a [Spring Boot](http://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/) application, packaged as an executable jar. You need JRE 8+ to start zipkin-server.

Span storage and transports are configurable. By default, storage is
in-memory, the http span transport (POST /spans endpoint) is enabled,
and the server listens on port 9411.

## Endpoints

The following endpoints are defined for Zipkin:
* / - [UI](https://github.com/openzipkin/zipkin/tree/master/zipkin-ui)
* /config.json - [Configuration for the UI](#configuration-for-the-ui)
* /api/v1 - [Api](http://zipkin.io/zipkin-api/#/)
* /health - Returns 200 status if OK

There are more [built-in endpoints](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-endpoints.html) provided by Spring Boot, such as `/metrics`. To comprehensively list endpoints, `GET /mappings`.

## Running locally

To run the server from the currently checked out source, enter the following.
```bash
$ ./mvnw -pl zipkin-server spring-boot:run
```

## Configuration for the UI
Zipkin has a web UI, which is enabled by default when you depend on `io.zipkin:zipkin-ui`. This UI is automatically included in the exec jar, and is hosted by default on port 9411.

When the UI loads, it reads default configuration from the `/config.json` endpoint. These values can be overridden by system properties.

Attribute | Property | Description
--- | --- | ---
environment | zipkin.ui.environment | The value here becomes a label in the top-right corner. Not required.
defaultLookback | zipkin.ui.default-lookback | Default duration in millis to look back when finding traces or dependency links. Affects the "Start time" element in the UI. Defaults to 604800000 (7 days in millis).
queryLimit | zipkin.ui.query-limit |  Default limit for Find Traces. Defaults to 10.

For example, if using docker you can set `JAVA_OPTS="-Dzipkin.ui.query-limit=100"` to affect `$.queryLimit` in `/config.json`.

## Environment Variables
zipkin-server is a drop-in replacement for the [scala query service](https://github.com/openzipkin/zipkin/tree/master/zipkin-query-service).

[yaml configuration](zipkin-server/src/main/resources/zipkin-server.yml) binds the following environment variables from zipkin-scala:

    * `QUERY_PORT`: Listen port for the http api and web ui; Defaults to 9411
    * `QUERY_LOG_LEVEL`: Log level written to the console; Defaults to INFO
    * `QUERY_LOOKBACK`: How many milliseconds queries look back from endTs; Defaults to 7 days
    * `STORAGE_TYPE`: SpanStore implementation: one of `mem`, `mysql`, `cassandra`, `elasticsearch`
    * `COLLECTOR_PORT`: Listen port for the scribe thrift api; Defaults to 9410 
    * `COLLECTOR_SAMPLE_RATE`: Percentage of traces to retain, defaults to always sample (1.0).

### Cassandra
The following apply when `STORAGE_TYPE` is set to `cassandra`:

    * `CASSANDRA_KEYSPACE`: The keyspace to use. Defaults to "zipkin".
    * `CASSANDRA_CONTACT_POINTS`: Comma separated list of hosts / ip addresses part of Cassandra cluster. Defaults to localhost
    * `CASSANDRA_LOCAL_DC`: Name of the datacenter that will be considered "local" for latency load balancing. When unset, load-balancing is round-robin.
    * `CASSANDRA_MAX_CONNECTIONS`: Max pooled connections per datacenter-local host. Defaults to 8
    * `CASSANDRA_ENSURE_SCHEMA`: Ensuring that schema exists, if enabled tries to execute script /zipkin-cassandra-core/resources/cassandra-schema-cql3.txt. Defaults to true
    * `CASSANDRA_USERNAME` and `CASSANDRA_PASSWORD`: Cassandra authentication. Will throw an exception on startup if authentication fails. No default
    * `CASSANDRA_SPAN_TTL`: Time-to-live in seconds for span data. Defaults to 604800 (7 days)
    * `CASSANDRA_INDEX_TTL`: Time-to-live in seconds for index data. Defaults to 259200 (3 days)

Example usage:

```bash
$ STORAGE_TYPE=cassandra CASSANDRA_CONTACT_POINTS=host1,host2 ./mvnw -pl zipkin-server spring-boot:run
```

### MySQL
The following apply when `STORAGE_TYPE` is set to `mysql`:

    * `MYSQL_DB`: The database to use. Defaults to "zipkin".
    * `MYSQL_USER` and `MYSQL_PASS`: MySQL authentication, which defaults to empty string.
    * `MYSQL_HOST`: Defaults to localhost
    * `MYSQL_TCP_PORT`: Defaults to 3306
    * `MYSQL_MAX_CONNECTIONS`: Maximum concurrent connections, defaults to 10
    * `MYSQL_USE_SSL`: Requires `javax.net.ssl.trustStore` and `javax.net.ssl.trustStorePassword`, defaults to false.

Example usage:

```bash
$ STORAGE_TYPE=mysql MYSQL_USER=root ./mvnw -pl zipkin-server spring-boot:run
```

### Elasticsearch
The following apply when `STORAGE_TYPE` is set to `elasticsearch`:

    * `ES_CLUSTER`: The name of the elasticsearch cluster to connect to. Defaults to "elasticsearch".
    * `ES_HOSTS`: A comma separated list of elasticsearch hostnodes to connect to, in host:port
                  format. The port should be the transport port, not the http port. Defaults to
                  "localhost:9300". Only one of these hosts needs to be available to fetch the
                  remaining nodes in the cluster. It is recommended to set this to all the master
                  nodes of the cluster.
    * `ES_INDEX`: The index prefix to use when generating daily index names. Defaults to zipkin.

Example usage:

```bash
$ STORAGE_TYPE=elasticsearch ES_CLUSTER=monitoring ES_HOSTS=host1:9300,host2:9300 ./mvnw -pl zipkin-server spring-boot:run
```

### Kafka
The following apply when `KAFKA_ZOOKEEPER` is set:

    * `KAFKA_ZOOKEEPER`: ZooKeeper host string, comma-separated host:port value. no default.
    * `KAFKA_TOPIC`: Defaults to zipkin
    * `KAFKA_GROUP_ID`: Consumer group this process is consuming on behalf of. Defaults to zipkin
    * `KAFKA_STREAMS`: Count of consumer threads consuming the topic. defaults to 1.

Example usage:

```bash
$ TRANSPORT_TYPE=kafka KAFKA_ZOOKEEPER=127.0.0.1:2181 ./mvnw -pl zipkin-server spring-boot:run
```

Example targeting Kafka running in Docker:

```bash
$ export KAFKA_ZOOKEEPER=$(docker-machine ip `docker-machine active`)
# Run Kafka in the background
$ docker run -d -p 2181:2181 -p 9092:9092 \
    --env ADVERTISED_HOST=$KAFKA_ZOOKEEPER \
    --env AUTO_CREATE_TOPICS=true \
    spotify/kafka
# Start the zipkin server, which reads $KAFKA_ZOOKEEPER
$ TRANSPORT_TYPE=kafka ./mvnw -pl zipkin-server spring-boot:run
```

## Running with Docker
Released versions of zipkin-server are published to Docker Hub as `openzipkin/zipkin-java`.
See [docker-zipkin-java](https://github.com/openzipkin/docker-zipkin-java) for details.
