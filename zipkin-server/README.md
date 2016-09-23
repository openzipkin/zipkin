# zipkin-server
zipkin-server is a [Spring Boot](http://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/) application, packaged as an executable jar. You need JRE 8+ to start zipkin-server.

Span storage and collectors are configurable. By default, storage is
in-memory, the http collector (POST /api/v1/spans endpoint) is enabled,
and the server listens on port 9411.

## Quick-start

The quickest way to get started is to fetch the [latest released server](https://search.maven.org/remote_content?g=io.zipkin.java&a=zipkin-server&v=LATEST&c=exec) as a self-contained executable jar. Note that the Zipkin server requires minimum JRE 8. For example:

```
wget -O zipkin.jar 'https://search.maven.org/remote_content?g=io.zipkin.java&a=zipkin-server&v=LATEST&c=exec'
java -jar zipkin.jar
```

Once you've started, browse to http://your_host:9411 to find traces!

## Endpoints

The following endpoints are defined under the base url http://your_host:9411
* / - [UI](https://github.com/openzipkin/zipkin/tree/master/zipkin-ui)
* /config.json - [Configuration for the UI](#configuration-for-the-ui)
* /api/v1 - [Api](http://zipkin.io/zipkin-api/#/)
* /health - Returns 200 status if OK
* /info - Provides the version of the running instance
* /metrics - Includes collector metrics broken down by transport type 

There are more [built-in endpoints](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-endpoints.html) provided by Spring Boot, such as `/metrics`. To comprehensively list endpoints, `GET /mappings`.

### CORS (Cross-origin Resource Sharing)

By default, all endpoints under `/api/v1` are configured to **allow** cross-origin requests.

This can be changed by modifying the YAML configuration file (`zipkin.query.allowed-origins`) or by setting an environment variable.

For example, to allow CORS requests from `http://foo.bar.com`:

```
ZIPKIN_QUERY_ALLOWED_ORIGINS=http://foo.bar.com
```

## Logging

By default, zipkin writes log messages to the console at INFO level and above. You can adjust categories using the `--logging.level.XXX` parameter, a `-Dlogging.level.XXX` system property, or by adjusting [yaml configuration](src/main/resources/zipkin-server.yml).

For example, if you want to enable debug logging for all zipkin categories, you can start the server like so:

```bash
$ java -jar zipkin.jar --logging.level.zipkin=DEBUG
```

Under the covers, the server uses [Spring Boot - Logback integration](http://docs.spring.io/spring-boot/docs/current/reference/html/howto-logging.html#howto-configure-logback-for-logging). For example, you can add `--logging.exception-conversion-word=%wEx{full}` to dump full stack traces instead of truncated ones.

## Metrics

Metrics are exported to the path `/metrics` and extend [defaults reported by spring-boot](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-metrics.html).

Metrics are also exported to the path `/prometheus` if the `zipkin-autoconfigure-metrics-prometheus` is available in the classpath.
See the prometheus metrics [README](../zipkin-autoconfigure/metrics-prometheus/README.md) for more information.

### Collector

Collector metrics are broken down by transport. The following are exported to the "/metrics" endpoint:

Metric | Description
--- | ---
counter.zipkin_collector.messages.$transport | cumulative messages received; should relate to messages reported by instrumented apps
counter.zipkin_collector.messages_dropped.$transport | cumulative messages dropped; reasons include client disconnects or malformed content
counter.zipkin_collector.bytes.$transport | cumulative message bytes
counter.zipkin_collector.spans.$transport | cumulative spans read; should relate to messages reported by instrumented apps
counter.zipkin_collector.spans_dropped.$transport | cumulative spans dropped; reasons include sampling or storage failures
gauge.zipkin_collector.message_spans.$transport | last count of spans in a message
gauge.zipkin_collector.message_bytes.$transport | last count of bytes in a message

## Self-Tracing
Self tracing exists to help troubleshoot performance of the zipkin-server. Production deployments
who enable self-tracing should lower the sample rate from 1.0 (100%) to a much smaller rate, like
0.001 (0.1% or 1 out of 1000).

When Brave dependencies are in the classpath, and `zipkin.self-tracing.enabled=true`,
Zipkin will self-trace calls to the api.

[yaml configuration](zipkin-server/src/main/resources/zipkin-server.yml) binds the following environment variables to spring properties:

Variable | Property | Description
--- | --- | ---
SELF_TRACING_ENABLED | zipkin.self-tracing.enabled | Set to true to enable self-tracing. Defaults to false
SELF_TRACING_SAMPLE_RATE`: Percentage of self-traces to retain, defaults to always sample (1.0).
SELF_TRACING_FLUSH_INTERVAL | zipkin.self-tracing.flush-interval | Interval in seconds to flush self-tracing data to storage. Defaults to 1

## Configuration for the UI
Zipkin has a web UI, which is enabled by default when you depend on `io.zipkin:zipkin-ui`. This UI is automatically included in the exec jar, and is hosted by default on port 9411.

When the UI loads, it reads default configuration from the `/config.json` endpoint. These values can be overridden by system properties or any other alternative [supported by Spring Boot](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html).

Attribute | Property | Description
--- | --- | ---
environment | zipkin.ui.environment | The value here becomes a label in the top-right corner. Not required.
defaultLookback | zipkin.ui.default-lookback | Default duration in millis to look back when finding traces. Affects the "Start time" element in the UI. Defaults to 3600000 (1 hour in millis).
queryLimit | zipkin.ui.query-limit | Default limit for Find Traces. Defaults to 10.
instrumented | zipkin.ui.instrumented | Which sites this Zipkin UI covers. Regex syntax. e.g. `http:\/\/example.com\/.*` Defaults to match all websites (`.*`).

For example, if using docker you can set `ZIPKIN_UI_QUERY_LIMIT=100` to affect `$.queryLimit` in `/config.json`.

## Environment Variables
zipkin-server is a drop-in replacement for the [scala query service](https://github.com/openzipkin/zipkin/tree/master/zipkin-query-service).

[yaml configuration](zipkin-server/src/main/resources/zipkin-server.yml) binds the following environment variables from zipkin-scala:

    * `QUERY_PORT`: Listen port for the http api and web ui; Defaults to 9411
    * `QUERY_LOG_LEVEL`: Log level written to the console; Defaults to INFO
    * `QUERY_LOOKBACK`: How many milliseconds queries can look back from endTs; Defaults to 7 days
    * `STORAGE_TYPE`: SpanStore implementation: one of `mem`, `mysql`, `cassandra`, `elasticsearch`
    * `COLLECTOR_PORT`: Listen port for the scribe thrift api; Defaults to 9410 
    * `COLLECTOR_SAMPLE_RATE`: Percentage of traces to retain, defaults to always sample (1.0).

### Cassandra Storage
Zipkin's [Cassandra storage component](../zipkin-storage/cassandra)
supports version 2.2+ and applies when `STORAGE_TYPE` is set to `cassandra`:

    * `CASSANDRA_KEYSPACE`: The keyspace to use. Defaults to "zipkin".
    * `CASSANDRA_CONTACT_POINTS`: Comma separated list of hosts / ip addresses part of Cassandra cluster. Defaults to localhost
    * `CASSANDRA_LOCAL_DC`: Name of the datacenter that will be considered "local" for latency load balancing. When unset, load-balancing is round-robin.
    * `CASSANDRA_ENSURE_SCHEMA`: Ensuring cassandra has the latest schema. If enabled tries to execute scripts in the classpath prefixed with `cassandra-schema-cql3`. Defaults to true
    * `CASSANDRA_USERNAME` and `CASSANDRA_PASSWORD`: Cassandra authentication. Will throw an exception on startup if authentication fails. No default

The following are tuning parameters which may not concern all users:

    * `CASSANDRA_MAX_CONNECTIONS`: Max pooled connections per datacenter-local host. Defaults to 8
    * `CASSANDRA_INDEX_CACHE_MAX`: Maximum trace index metadata entries to cache. Zero disables caching. Defaults to 100000.
    * `CASSANDRA_INDEX_CACHE_TTL`: How many seconds to cache index metadata about a trace. Defaults to 60.
    * `CASSANDRA_INDEX_FETCH_MULTIPLIER`: How many more index rows to fetch than the user-supplied query limit. Defaults to 3.

Example usage:

```bash
$ STORAGE_TYPE=cassandra java -jar zipkin.jar --logging.level.com.datastax.driver.core.QueryLogger=trace
```
### MySQL Storage
The following apply when `STORAGE_TYPE` is set to `mysql`:

    * `MYSQL_DB`: The database to use. Defaults to "zipkin".
    * `MYSQL_USER` and `MYSQL_PASS`: MySQL authentication, which defaults to empty string.
    * `MYSQL_HOST`: Defaults to localhost
    * `MYSQL_TCP_PORT`: Defaults to 3306
    * `MYSQL_MAX_CONNECTIONS`: Maximum concurrent connections, defaults to 10
    * `MYSQL_USE_SSL`: Requires `javax.net.ssl.trustStore` and `javax.net.ssl.trustStorePassword`, defaults to false.

Example usage:

```bash
$ STORAGE_TYPE=mysql MYSQL_USER=root java -jar zipkin.jar
```

### Elasticsearch Storage
The following apply when `STORAGE_TYPE` is set to `elasticsearch`:

    * `ES_CLUSTER`: The name of the elasticsearch cluster to connect to. Defaults to "elasticsearch".
    * `ES_HOSTS`: A comma separated list of elasticsearch hostnodes to connect to. When in host:port
                  format, they should use the transport port, not the http port. To use http, specify
                  base urls, ex. http://host:9200. Defaults to "localhost:9300". When not using http,
                  Only one of the hosts needs to be available to fetch the remaining nodes in the
                  cluster. It is recommended to set this to all the master nodes of the cluster.
                  
                  If the http URL is an AWS-hosted elasticsearch installation (e.g.
                  https://search-domain-xyzzy.us-west-2.es.amazonaws.com) then Zipkin will attempt to 
                  use the default AWS credential provider (env variables, system properties, config
                  files, or ec2 profiles) to sign outbound requests to the cluster.
    * `ES_AWS_DOMAIN`: The name of the AWS-hosted elasticsearch domain to use. Supercedes any set 
                       `ES_HOSTS`. Triggers the same request signing behavior as with `ES_HOSTS`, but
                       requires the additional IAM permission to describe the given domain.
    * `ES_AWS_REGION`: An optional override to the default region lookup to search for the domain 
                       given in `ES_AWS_DOMAIN`. Ignored if only `ES_HOSTS` is present. 
    * `ES_INDEX`: The index prefix to use when generating daily index names. Defaults to zipkin.
    * `ES_INDEX_SHARDS`: The number of shards to split the index into. Each shard and its replicas
                         are assigned to a machine in the cluster. Increasing the number of shards
                         and machines in the cluster will improve read and write performance. Number
                         of shards cannot be changed for existing indices, but new daily indices
                         will pick up changes to the setting. Defaults to 5.
    * `ES_INDEX_REPLICAS`: The number of replica copies of each shard in the index. Each shard and
                           its replicas are assigned to a machine in the cluster. Increasing the
                           number of replicas and machines in the cluster will improve read
                           performance, but not write performance. Number of replicas can be changed
                           for existing indices. Defaults to 1. It is highly discouraged to set this
                           to 0 as it would mean a machine failure results in data loss.
Example usage:

To connect with http:
```bash
$ STORAGE_TYPE=elasticsearch ES_HOSTS=http://localhost:9200 java -jar zipkin.jar
```

Or to use transport client.
```bash
$ STORAGE_TYPE=elasticsearch ES_CLUSTER=monitoring ES_HOSTS=host1:9300,host2:9300 java -jar zipkin.jar
```

Or to use the Amazon Elasticsearch Service.
```bash
# make sure your cli credentials are setup as zipkin will read them
$ aws es describe-elasticsearch-domain --domain-name mydomain|jq .DomainStatus.Endpoint
"search-mydomain-2rlih66ibw43ftlk4342ceeewu.ap-southeast-1.es.amazonaws.com"
$ STORAGE_TYPE=elasticsearch ES_HOSTS=https://search-mydomain-2rlih66ibw43ftlk4342ceeewu.ap-southeast-1.es.amazonaws.com java -jar zipkin.jar
# or if you don't know the url already
$ STORAGE_TYPE=elasticsearch ES_AWS_DOMAIN=mycluster ES_AWS_REGION=ap-southeast-1 java -jar zipkin.jar
```

### Scribe Collector
The Scribe collector is enabled by default, configured by the following:

    * `SCRIBE_ENABLED`: Set to false to prevent scribe from starting; Defaults to true
    * `COLLECTOR_PORT`: Listen port for the scribe thrift api; Defaults to 9410

### Kafka Collector
This collector remains a Kafka 0.8.x consumer, while Zipkin systems update to 0.9+.

The following apply when `KAFKA_ZOOKEEPER` is set:

    * `KAFKA_TOPIC`: Topic zipkin spans will be consumed from. Defaults to "zipkin"
    * `KAFKA_STREAMS`: Count of threads/streams consuming the topic. Defaults to 1

Settings below correspond to "Old Consumer Configs" in [Kafka documentation](http://kafka.apache.org/documentation.html)

Variable | Old Consumer Config | Description
--- | --- | ---
KAFKA_ZOOKEEPER | zookeeper.connect | The zookeeper connect string, ex. 127.0.0.1:2181. No default
KAFKA_GROUP_ID | group.id | The consumer group this process is consuming on behalf of. Defaults to "zipkin"
KAFKA_MAX_MESSAGE_SIZE | fetch.message.max.bytes | Maximum size of a message containing spans in bytes. Defaults to 1 MiB

Example usage:

```bash
$ KAFKA_ZOOKEEPER=127.0.0.1:2181 java -jar zipkin.jar
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
$ java -jar zipkin.jar
```

## Running with Docker
Released versions of zipkin-server are published to Docker Hub as `openzipkin/zipkin`.
See [docker-zipkin-java](https://github.com/openzipkin/docker-zipkin-java) for details.

## Building locally

To build and run the server from the currently checked out source, enter the following.
```bash
# Build the server and also make its dependencies
$ ./mvnw -DskipTests --also-make -pl zipkin-server clean install
# Run the server
$ java -jar ./zipkin-server/target/zipkin-server-*exec.jar
```
