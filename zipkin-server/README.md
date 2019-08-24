# zipkin-server
zipkin-server is a [Spring Boot](http://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/) application, packaged as an executable jar. You need JRE 8+ to start zipkin-server.

Span storage and collectors are configurable. By default, storage is
in-memory, the http collector (POST /api/v2/spans endpoint) is enabled,
and the server listens on port 9411.

## Quick-start

The quickest way to get started is to fetch the [latest released server](https://search.maven.org/remote_content?g=io.zipkin&a=zipkin-server&v=LATEST&c=exec) as a self-contained executable jar. Note that the Zipkin server requires minimum JRE 8. For example:

```bash
$ curl -sSL https://zipkin.io/quickstart.sh | bash -s
$ java -jar zipkin.jar
```

Once you've started, browse to http://your_host:9411 to find traces!

## Endpoints

The following endpoints are defined under the base url http://your_host:9411
* / - [UI](../zipkin-ui)
* /config.json - [Configuration for the UI](#configuration-for-the-ui)
* /api/v2 - [Api](https://zipkin.io/zipkin-api/#/)
* /health - Returns 200 status if OK
* /info - Provides the version of the running instance
* /metrics - Includes collector metrics broken down by transport type

There are more [built-in endpoints](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-endpoints.html) provided by Spring Boot, such as `/metrics`. To comprehensively list endpoints, `GET /mappings`.

The [legacy /api/v1 Api](https://zipkin.io/zipkin-api/#/) is still supported. Backends are decoupled from the
HTTP api via data conversion. This means you can still accept legacy data on new backends and visa versa. Enter
`https://zipkin.io/zipkin-api/zipkin-api.yaml` into the explore box of the Swagger UI to view the old definition

### CORS (Cross-origin Resource Sharing)

By default, all endpoints under `/api/v2` are configured to **allow** cross-origin requests.

This can be changed by modifying the YAML configuration file (`zipkin.query.allowed-origins`) or by setting an environment variable.

For example, to allow CORS requests from `http://foo.bar.com`:

```
ZIPKIN_QUERY_ALLOWED_ORIGINS=http://foo.bar.com
```

## Logging

By default, zipkin writes log messages to the console at INFO level and above. You can adjust categories using the `--logging.level.XXX` parameter, a `-Dlogging.level.XXX` system property, or by adjusting [yaml configuration](src/main/resources/zipkin-server-shared.yml).

For example, if you want to enable debug logging for all zipkin categories, you can start the server like so:

```bash
$ java -jar zipkin.jar --logging.level.zipkin2=DEBUG
```

Under the covers, the server uses [Spring Boot - Logback integration](http://docs.spring.io/spring-boot/docs/current/reference/html/howto-logging.html#howto-configure-logback-for-logging). For example, you can add `--logging.exception-conversion-word=%wEx{full}` to dump full stack traces instead of truncated ones.

## Metrics

Metrics are exported to the path `/metrics` and extend [defaults reported by spring-boot](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-metrics.html).
They are also exported to the path `/prometheus`.

### Example Prometheus configuration
Here's an example `/prometheus` configuration, using the Prometheus
exposition [text format version 0.0.4](https://prometheus.io/docs/instrumenting/exposition_formats/)

```yaml
  - job_name: 'zipkin'
    scrape_interval: 5s
    metrics_path: '/prometheus'
    static_configs:
      - targets: ['localhost:9411']
    metric_relabel_configs:
      # Response code count
      - source_labels: [__name__]
        regex: '^status_(\d+)_(.*)$'
        replacement: '${1}'
        target_label: status
      - source_labels: [__name__]
        regex: '^status_(\d+)_(.*)$'
        replacement: '${2}'
        target_label: path
      - source_labels: [__name__]
        regex: '^status_(\d+)_(.*)$'
        replacement: 'http_requests_total'
        target_label: __name__
```

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

[yaml configuration](src/main/resources/zipkin-server-shared.yml) binds the following environment variables to spring properties:

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
defaultLookback | zipkin.ui.default-lookback | Default duration in millis to look back when finding traces. Affects the "Start time" element in the UI. Defaults to 900000 (15 minutes in millis).
searchEnabled | zipkin.ui.search-enabled | If the Find Traces screen is enabled. Defaults to true.
queryLimit | zipkin.ui.query-limit | Default limit for Find Traces. Defaults to 10.
instrumented | zipkin.ui.instrumented | Which sites this Zipkin UI covers. Regex syntax. e.g. `http:\/\/example.com\/.*` Defaults to match all websites (`.*`).
logsUrl | zipkin.ui.logs-url | Logs query service url pattern. If specified, a button will appear on the trace page and will replace {traceId} in the url by the traceId. Not required.
dependency.lowErrorRate | zipkin.ui.dependency.low-error-rate | The rate of error calls on a dependency link that turns it yellow. Defaults to 0.5 (50%) set to >1 to disable.
dependency.highErrorRate | zipkin.ui.dependency.high-error-rate | The rate of error calls on a dependency link that turns it red. Defaults to 0.75 (75%) set to >1 to disable.
basePath | zipkin.ui.basepath | path prefix placed into the <base> tag in the UI HTML; useful when running behind a reverse proxy. Default "/zipkin"
suggestLens | zipkin.ui.suggest-lens | When true, a button will appear on the navigation bar, when pressed switches to the Lens Ui (for Beta testing). Default true

For example, if using docker you can set `ZIPKIN_UI_QUERY_LIMIT=100` to affect `$.queryLimit` in `/config.json`.

## Environment Variables
zipkin-server is a drop-in replacement for the [scala query service](https://github.com/openzipkin/zipkin/tree/scala/zipkin-query-service).

[yaml configuration](src/main/resources/zipkin-server-shared.yml) binds the following environment variables from zipkin-scala:

* `QUERY_PORT`: Listen port for the http api and web ui; Defaults to 9411
* `QUERY_ENABLED`: `false` disables the query api and UI assets. Search
may also be disabled for the storage backend if it is not needed;
Defaults to true
* `SEARCH_ENABLED`: `false` disables trace search requests on the storage
backend. Does not disable trace by ID or dependency queries. Disable this
when you use another service (such as logs) to find trace IDs;
Defaults to true
* `QUERY_LOG_LEVEL`: Log level written to the console; Defaults to INFO
* `QUERY_LOOKBACK`: How many milliseconds queries can look back from endTs; Defaults to 24 hours (two daily buckets: one for today and one for yesterday)
* `STORAGE_TYPE`: SpanStore implementation: one of `mem`, `mysql`, `cassandra`, `elasticsearch`
* `COLLECTOR_SAMPLE_RATE`: Percentage of traces to retain, defaults to always sample (1.0).
* `AUTOCOMPLETE_KEYS`: list of span tag keys which will be returned by the `/api/v2/autocompleteTags` endpoint; Tag keys should be comma separated e.g. "instance_id,user_id,env"
* `AUTOCOMPLETE_TTL`: How long in milliseconds to suppress calls to write the same autocomplete key/value pair. Default 3600000 (1 hr) 

### In-Memory Storage
Zipkin's In-Memory Storage is the default storage component that is used when no other storage type is configured. By default it stores a maximum of 500000 spans. Oldest traces (and their spans) will be purged first when this limit is exceeded. If you encounter out-of-memory errors, increase the heap size (-Xmx).

Example usage:
```bash
$ java -jar zipkin.jar
```
You can override the maximum number of spans stored using the `--max-spans` application parameter:
```bash
$ java -Xmx1G -jar zipkin.jar --max-spans=1000000
```

Note this storage component was primarily developed for testing and as a means to get Zipkin server up and running quickly without external dependencies. It is not viable for high work loads.  

### Throttled Storage (Experimental)
These settings can be used to help tune the rate at which Zipkin flushes data to another, underlying `StorageComponent` (such as Elasticsearch):

    * `STORAGE_THROTTLE_ENABLED`: Enables throttling
    * `STORAGE_THROTTLE_MIN_CONCURRENCY`: Minimum number of Threads to use for writing to storage.
    * `STORAGE_THROTTLE_MAX_CONCURRENCY`: Maximum number of Threads to use for writing to storage.
    * `STORAGE_THROTTLE_MAX_QUEUE_SIZE`: How many messages to buffer while all Threads are writing data before abandoning a message (0 = no buffering).

As this feature is experimental, it is not recommended to run this in production environments.

### Cassandra Storage
Zipkin's [Cassandra storage component](../zipkin-storage/cassandra)
supports version 3.11+ and applies when `STORAGE_TYPE` is set to `cassandra3`:

    * `CASSANDRA_KEYSPACE`: The keyspace to use. Defaults to "zipkin2"
    * `CASSANDRA_CONTACT_POINTS`: Comma separated list of host addresses part of Cassandra cluster. You can also specify a custom port with 'host:port'. Defaults to localhost on port 9042.
    * `CASSANDRA_LOCAL_DC`: Name of the datacenter that will be considered "local" for latency load balancing. When unset, load-balancing is round-robin.
    * `CASSANDRA_ENSURE_SCHEMA`: Ensuring cassandra has the latest schema. If enabled tries to execute scripts in the classpath prefixed with `cassandra-schema-cql3`. Defaults to true
    * `CASSANDRA_USERNAME` and `CASSANDRA_PASSWORD`: Cassandra authentication. Will throw an exception on startup if authentication fails. No default
    * `CASSANDRA_USE_SSL`: Requires `javax.net.ssl.trustStore` and `javax.net.ssl.trustStorePassword`, defaults to false.

The following are tuning parameters which may not concern all users:

    * `CASSANDRA_MAX_CONNECTIONS`: Max pooled connections per datacenter-local host. Defaults to 8
    * `CASSANDRA_INDEX_CACHE_MAX`: Maximum trace index metadata entries to cache. Zero disables caching. Defaults to 100000.
    * `CASSANDRA_INDEX_CACHE_TTL`: How many seconds to cache index metadata about a trace. Defaults to 60.
    * `CASSANDRA_INDEX_FETCH_MULTIPLIER`: How many more index rows to fetch than the user-supplied query limit. Defaults to 3.

Example usage with logging:

```bash
$ STORAGE_TYPE=cassandra3 java -jar zipkin.jar --logging.level.zipkin=trace --logging.level.zipkin2=trace --logging.level.com.datastax.driver.core=debug
```

### Elasticsearch Storage
Zipkin's [Elasticsearch storage component](../zipkin-storage/elasticsearch)
supports versions 5-7.x and applies when `STORAGE_TYPE` is set to `elasticsearch`

The following apply when `STORAGE_TYPE` is set to `elasticsearch`:

    * `ES_HOSTS`: A comma separated list of elasticsearch base urls to connect to ex. http://host:9200.
                  Defaults to "http://localhost:9200".
    * `ES_PIPELINE`: Indicates the ingest pipeline used before spans are indexed. No default.
    * `ES_TIMEOUT`: Controls the connect, read and write socket timeouts (in milliseconds) for
                    Elasticsearch Api. Defaults to 10000 (10 seconds)
    * `ES_INDEX`: The index prefix to use when generating daily index names. Defaults to zipkin.
    * `ES_DATE_SEPARATOR`: The date separator to use when generating daily index names. Defaults to '-'.
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
    * `ES_USERNAME` and `ES_PASSWORD`: Elasticsearch basic authentication, which defaults to empty string.
                                       Use when X-Pack security (formerly Shield) is in place.
    * `ES_HTTP_LOGGING`: When set, controls the volume of HTTP logging of the Elasticsearch Api.
                         Options are BASIC, HEADERS, BODY
Example usage:

To connect normally:
```bash
$ STORAGE_TYPE=elasticsearch ES_HOSTS=http://myhost:9200 java -jar zipkin.jar
```

To log Elasticsearch api requests:
```bash
$ STORAGE_TYPE=elasticsearch ES_HTTP_LOGGING=BASIC java -jar zipkin.jar
```

#### Using a custom Key Store or Trust Store (SSL)
If your Elasticsearch endpoint customized SSL configuration (for example self-signed) certificates,
you can use any of the following [subset of JSSE properties](https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#T6) to connect.

 * javax.net.ssl.keyStore
 * javax.net.ssl.keyStorePassword
 * javax.net.ssl.keyStoreType
 * javax.net.ssl.trustStore
 * javax.net.ssl.trustStorePassword
 * javax.net.ssl.trustStoreType

Usage example:
```bash
$ JAVA_OPTS='-Djavax.net.ssl.keyStore=keystore.p12 -Djavax.net.ssl.keyStorePassword=keypassword -Djavax.net.ssl.keyStoreType=PKCS12 -Djavax.net.ssl.trustStore=truststore.p12 -Djavax.net.ssl.trustStorePassword=trustpassword -Djavax.net.ssl.trustStoreType=PKCS12'
$ STORAGE_TYPE=elasticsearch java $JAVA_OPTS -jar zipkin.jar
```

Under the scenes, these map to properties prefixed `zipkin.storage.elasticsearch.ssl.`, which affect
the Armeria client used to connect to Elasticsearch.

The above properties allow the most common SSL setup to work out of box. If you need more
customization, please make a comment in [this issue](https://github.com/openzipkin/zipkin/issues/2774).

### Legacy (v1) storage components
The following components are no longer encouraged, but exist to help aid
transition to supported ones. These are indicated as "v1" as they use
data layouts based on Zipkin's V1 Thrift model, as opposed to the
simpler v2 data model currently used.

#### MySQL Storage
Zipkin's [MySQL component](../zipkin-storage/mysql-v1) is tested against MySQL
5.7 and applies when `STORAGE_TYPE` is set to `mysql`:

    * `MYSQL_DB`: The database to use. Defaults to "zipkin".
    * `MYSQL_USER` and `MYSQL_PASS`: MySQL authentication, which defaults to empty string.
    * `MYSQL_HOST`: Defaults to localhost
    * `MYSQL_TCP_PORT`: Defaults to 3306
    * `MYSQL_MAX_CONNECTIONS`: Maximum concurrent connections, defaults to 10
    * `MYSQL_USE_SSL`: Requires `javax.net.ssl.trustStore` and `javax.net.ssl.trustStorePassword`, defaults to false.

Note: This module is not recommended for production usage. Before using this,
you must [apply the schema](../zipkin-storage/mysql-v1#applying-the-schema).

Alternatively you can use `MYSQL_JDBC_URL` and specify the complete JDBC url yourself. Note that the URL constructed by
using the separate settings above will also include the following parameters: 
`?autoReconnect=true&useSSL=false&useUnicode=yes&characterEncoding=UTF-8`. If you specify the JDBC url yourself, add
these parameters as well.

Example usage:

```bash
$ STORAGE_TYPE=mysql MYSQL_USER=root java -jar zipkin.jar
```

### Cassandra Storage
Zipkin's [Legacy (v1) Cassandra storage component](../zipkin-storage/cassandra-v1)
supports version 2.2+ and applies when `STORAGE_TYPE` is set to `cassandra`:

The environment variables are the same as `STORAGE_TYPE=cassandra3`,
except the default keyspace name is "zipkin".

Example usage:

```bash
$ STORAGE_TYPE=cassandra java -jar zipkin.jar
```

#### Service and Span names query
The [Zipkin Api](https://zipkin.io/zipkin-api/#/default/get_services) does not include
a parameter for how far back to look for service or span names. In order
to prevent excessive load, service and span name queries are limited by
`QUERY_LOOKBACK`, which defaults to 24hrs (two daily buckets: one for
today and one for yesterday)

### HTTP Collector
The HTTP collector is enabled by default. It accepts spans via `POST /api/v1/spans` and `POST /api/v2/spans`.
The HTTP collector supports the following configuration:

Property | Environment Variable | Description
--- | --- | ---
`zipkin.collector.http.enabled` | `COLLECTOR_HTTP_ENABLED` | `false` disables the HTTP collector. Defaults to `true`.

### Scribe (Legacy) Collector
A collector supporting Scribe is enabled when `COLLECTOR_SCRIBE_ENABLED=true`. New
sites are discouraged from using this collector as Scribe is an archived
technology.

Environment Variable | Property | Description
--- | --- | ---
`COLLECTOR_PORT` | `zipkin.collector.scribe.port` | The port to listen for thrift RPC scribe requests. Defaults to 9410
`SCRIBE_CATEGORY` | `zipkin.collector.scribe.category` | Category zipkin spans will be consumed from. Defaults to `zipkin`


### ActiveMQ Collector
The [ActiveMQ Collector](../zipkin-collector/activemq) is enabled when `ACTIVEMQ_URL` is set to a v5.x broker. The following settings apply in this case.

Environment Variable | Property | Description
--- | --- | ---
`COLLECTOR_ACTIVEMQ_ENABLED` | `zipkin.collector.activemq.enabled` | `false` disables the ActiveMQ collector. Defaults to `true`.
`ACTIVEMQ_URL` | `zipkin.collector.activemq.url` | [Connection URL](https://activemq.apache.org/uri-protocols) to the ActiveMQ broker, ex. `tcp://localhost:61616` or `failover:(tcp://localhost:61616,tcp://remotehost:61616)`
`ACTIVEMQ_QUEUE` | `zipkin.collector.activemq.queue` | Queue from which to collect span messages. Defaults to `zipkin`
`ACTIVEMQ_CLIENT_ID_PREFIX` | `zipkin.collector.activemq.client-id-prefix` | Client ID prefix for queue consumers. Defaults to `zipkin`
`ACTIVEMQ_CONCURRENCY` | `zipkin.collector.activemq.concurrency` | Number of concurrent span consumers. Defaults to `1`
`ACTIVEMQ_USER` | `zipkin.collector.activemq.user` | Optional username to connect to the broker
`ACTIVEMQ_PASSWORD`| `zipkin.collector.activemq.password` | Optional password to connect to the broker

Example usage:

```bash
$ ACTIVEMQ_URL=tcp://localhost:61616 java -jar zipkin.jar
```

### Kafka Collector
The Kafka collector is enabled when `KAFKA_BOOTSTRAP_SERVERS` is set to
a v0.10+ server. The following settings apply in this case. Some settings
correspond to "New Consumer Configs" in [Kafka documentation](https://kafka.apache.org/documentation/#newconsumerconfigs).

Variable | New Consumer Config | Description
--- | --- | ---
`COLLECTOR_KAFKA_ENABLED` | N/A | `false` disables the Kafka collector. Defaults to `true`.
`KAFKA_BOOTSTRAP_SERVERS` | bootstrap.servers | Comma-separated list of brokers, ex. 127.0.0.1:9092. No default
`KAFKA_GROUP_ID` | group.id | The consumer group this process is consuming on behalf of. Defaults to `zipkin`
`KAFKA_TOPIC` | N/A | Comma-separated list of topics that zipkin spans will be consumed from. Defaults to `zipkin`
`KAFKA_STREAMS` | N/A | Count of threads consuming the topic. Defaults to `1`

Example usage:

```bash
$ KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092 \
    java -jar zipkin.jar
```

#### Other Kafka consumer properties
You may need to set other
[Kafka consumer properties](https://kafka.apache.org/documentation/#newconsumerconfigs), in
addition to the ones with explicit properties defined by the collector. In this case, you need to
prefix that property name with `zipkin.collector.kafka.overrides` and pass it as a system property
argument.

For example, to override `auto.offset.reset`, you can set a system property named
`zipkin.collector.kafka.overrides.auto.offset.reset`:

```bash
$ KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092 \
    java -Dzipkin.collector.kafka.overrides.auto.offset.reset=largest -jar zipkin.jar
```

#### Detailed examples

Example targeting Kafka running in Docker:

```bash
$ export KAFKA_BOOTSTRAP_SERVERS=$(docker-machine ip `docker-machine active`)
# Run Kafka in the background
$ docker run -d -p 9092:9092 \
    --env ADVERTISED_HOST=$KAFKA_BOOTSTRAP_SERVERS \
    --env AUTO_CREATE_TOPICS=true \
    spotify/kafka
# Start the zipkin server, which reads $KAFKA_BOOTSTRAP_SERVERS
$ java -jar zipkin.jar
```

Multiple bootstrap servers:

```bash
$ KAFKA_BOOTSTRAP_SERVERS=broker1.local:9092,broker2.local:9092 \
    java -jar zipkin.jar
```

Alternate topic name(s):

```bash
$ KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9092 \
    java -Dzipkin.collector.kafka.topic=zapkin,zipken -jar zipkin.jar
```

Specifying bootstrap servers as a system property, instead of an environment variable:

```bash
$ java -Dzipkin.collector.kafka.bootstrap-servers=127.0.0.1:9092 \
    -jar zipkin.jar
```

### RabbitMQ collector
The [RabbitMQ collector](../zipkin-collector/rabbitmq) will be enabled when the `addresses` or `uri` for the RabbitMQ server(s) is set.

Example usage:

```bash
$ RABBIT_ADDRESSES=localhost java -jar zipkin.jar
```

### gRPC Collector (Experimental)
You can enable a gRPC span collector endpoint by setting `COLLECTOR_GRPC_ENABLED=true`. The
`zipkin.proto3.SpanService/Report` endpoint will run on the same port as normal http (9411).


Example usage:

```bash
$ COLLECTOR_GRPC_ENABLED=true java -jar zipkin.jar
```

As this service is experimental, it is not recommended to run this in production environments.

### 128-bit trace IDs

Zipkin supports 64 and 128-bit trace identifiers, typically serialized
as 16 or 32 character hex strings. By default, spans reported to zipkin
with the same trace ID will be considered in the same trace.

For example, `463ac35c9f6413ad48485a3953bb6124` is a 128-bit trace ID,
while `48485a3953bb6124` is a 64-bit one.

Note: Span (or parent) IDs within a trace are 64-bit regardless of the
length or value of their trace ID.

#### Migrating from 64 to 128-bit trace IDs

Unless you only issue 128-bit traces when all applications support them,
the process of updating applications from 64 to 128-bit trace IDs results
in a mixed state. This mixed state is mitigated by the setting
`STRICT_TRACE_ID=false`, explained below. Once a migration is complete,
remove the setting `STRICT_TRACE_ID=false` or set it to true.

Here are a few trace IDs the help what happens during this setting.

* Trace ID A: 463ac35c9f6413ad48485a3953bb6124
* Trace ID B: 48485a3953bb6124
* Trace ID C: 463ac35c9f6413adf1a48a8cff464e0e
* Trace ID D: 463ac35c9f6413ad

In a 64-bit environment, trace IDs will look like B or D above. When an
application upgrades to 128-bit instrumentation and decides to create a
128-bit trace, its trace IDs will look like A or C above.

Applications who aren't yet 128-bit capable typically only retain the
right-most 16 characters of the trace ID. When this happens, the same
trace could be reported as trace ID A or trace ID B.

By default, Zipkin will think these are different trace IDs, as they are
different strings. During a transition from 64-128 bit trace IDs, spans
would appear split across two IDs. For example, it might start as trace
ID A, but the next hop might truncate it to trace ID B. This would render
the system unusable for applications performing upgrades.

One way to address this problem is to not use 128-bit trace IDs until
all applications support them. This prevents a mixed scenario at the cost
of coordination. Another way is to set `STRICT_TRACE_ID=false`.

When `STRICT_TRACE_ID=false`, only the right-most 16 of a 32 character
trace ID are considered when grouping or retrieving traces. This setting
should only be applied when transitioning from 64 to 128-bit trace IDs
and removed once the transition is complete.

See https://github.com/openzipkin/b3-propagation/issues/6 for the status
of known open source libraries on 128-bit trace identifiers.

See `zipkin2.storage.StorageComponent.Builder` for even more details!

## Running with Docker
Released versions of zipkin-server are published to Docker Hub as `openzipkin/zipkin`.
See [docker-zipkin](https://github.com/openzipkin/docker-zipkin) for details.

## Building locally

To build and run the server from the currently checked out source, enter the following.
```bash
# Build the server and also make its dependencies
$ ./mvnw -Dlicense.skip=true -DskipTests --also-make -pl zipkin-server clean install
# Run the server
$ java -jar ./zipkin-server/target/zipkin-server-*exec.jar
```
