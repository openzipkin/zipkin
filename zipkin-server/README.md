# zipkin-server
Zipkin Server is a Java 1.11+ service, packaged as an executable jar.

Span storage and collectors are [configurable](#configuration). By default, storage is in-memory,
the HTTP collector (POST /api/v2/spans endpoint) is enabled, and the server listens on port 9411.

Zipkin Server is implemented with [Armeria](https://github.com/line/armeria) and based on the [SkyWalking v9](https://github.com/apache/skywalking) as core. 

## Custom servers are not supported

By Custom servers we mean trying to use/embed `zipkin` as part of _an application you package_ (e.g. adding `zipkin-server` dependency to an application) instead of the packaged application we release.

For proper usage, see the guides below.

## Quick-start

The quickest way to get started is to fetch the [latest released server](https://search.maven.org/remote_content?g=io.zipkin&a=zipkin-server&v=LATEST&c=exec) as a self-contained executable jar. Note that the Zipkin server requires minimum JRE 8. For example:

```bash
$ curl -sSL https://zipkin.io/quickstart.sh | bash -s
$ java -jar zipkin.jar
```

Once you've started, browse to http://your_host:9411 to find traces!

## Endpoints

The following endpoints are defined under the base url http://your_host:9411
* / - [UI](../zipkin-lens)
* /config.json - Configuration for the UI
* /api/v2 - [API](https://zipkin.io/zipkin-api/#/)
* /health - Returns 200 status if OK
* /info - Provides the version of the running instance
* /metrics - Includes collector metrics broken down by transport type
* /prometheus - Prometheus scrape endpoint

The [legacy /api/v1 API](https://zipkin.io/zipkin-api/#/) is still supported. Backends are decoupled from the
HTTP API via data conversion. This means you can still accept legacy data on new backends and visa versa. Enter
`https://zipkin.io/zipkin-api/zipkin-api.yaml` into the explore box of the Swagger UI to view the old definition

### CORS (Cross-origin Resource Sharing)

By default, all endpoints under `/api/v2` are configured to **allow** cross-origin requests.

This can be changed by modifying the property `query-zipkin.zipkin.allowedOrigins` or environment `ZIPKIN_QUERY_ALLOWED_ORIGINS`.

For example, to allow CORS requests from `http://foo.bar.com`:

```
ZIPKIN_QUERY_ALLOWED_ORIGINS=http://foo.bar.com
```

See [Configuration](#configuration) for more about how Zipkin is configured.

### Service and Span names query
The [Zipkin API](https://zipkin.io/zipkin-api/#/default/get_services) does not include
a parameter for how far back to look for service or span names. In order
to prevent excessive load, service and span name queries are limited by
`ZIPKIN_QUERY_LOOKBACK`, which defaults to 24hrs (two daily buckets: one for
today and one for yesterday)

## Logging

By default, zipkin writes log messages to the console at INFO level and above. You can adjust
categories using the `log.level` property.

For example, if you want to enable debug logging, you can start the server like so:

```bash
$ java -Dlog.level=DEBUG -jar zipkin.jar
```

See [Configuration](#configuration) for more about how Zipkin is configured.

### Advanced Logging Configuration
Under the covers, the server uses [Log4j2](https://logging.apache.org/log4j/2.x/).
For example, you can add `-Dlog4j.configurationFile=path/to/log4j2-config.xml` to customize the configuration file of logging.

## Metrics

Collector Metrics are exported to the path `/metrics`. These and additional metrics are exported
to the path `/prometheus`.

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

Metric | Tags | Description
--- | --- | ---
trace_in_latency_bucket_bucket | sw_backend_instance=$instance_host_$instance_port, protocol=$transport, le=$second_bucket | The process latency histogram of trace data
trace_in_latency_sum | sw_backend_instance=$instance_host_$instance_port, protocol=$transport | The total process latency of trace data
trace_analysis_error_count | sw_backend_instance=$instance_host_$instance_port, protocol=$transport | The error number of trace analysis

## Configuration
We support ENV variable configuration, such as `ZIPKIN_STORAGE=cassandra`, as they are familiar to
administrators and easy to use in runtime environments such as Docker.

Here are the top-level key configuration of Zipkin:
* `ZIPKIN_SERVICE_NAME_MAX_LENGTH`: Maximum length of a service name; Defaults to 70
* `ZIPKIN_ENDPOINT_NAME_MAX_LENGTH`: Maximum length of an endpoint name; Defaults to 150
* `ZIPKIN_CORE_RECORD_DATA_TTL`: How long to keep tracing data, in days; Defaults to 7 days
* `ZIPKIN_CORE_METRICS_DATA_TTL`: How long to keep metrics data(such as service names, span names), in days; Defaults to 7 days
* `ZIPKIN_SEARCHABLE_TAG_KEYS`: Defines a set of span tag keys which are searchable. Defaults to `http.method`
* `ZIPKIN_SAMPLE_RATE`: The trace sample rate precision is 0.0001, should be between 0 and 1. Defaults to 1
* `ZIPKIN_SERVER_PORT`: Listen HTTP, gRPC port for HTTP API, web UI, etc. Defaults to 9411
* `ZIPKIN_SEARCH_ENABLED`: `false` disables searching in the query API and any indexing or post-processing
in the collector to support search. This does not disable the entire UI, as trace by ID and
dependency queries still operate. Disable this when you use another service (such as logs) to find
trace IDs. Defaults to true
* `ZIPKIN_STORAGE`: Storage of the tracing data: one of `elasticsearch`, `h2`, `mysql`, `postgresql`, `banyandb`, `cassandra`

### HTTP Service

The server provides multiple HTTP services, which by default use the same IP and port to provide services externally. 
If each service is configured with a different port, a new HTTP service would be started and provided for that specific service.

The following several HTTP services are available：
* `ZIPKIN_QUERY_REST_PORT`: Listen HTTP port for HTTP API and web UI to other port, If less than or equal to 0, `ZIPKIN_SERVER_PORT` would be used. Defaults to -1
* `ZIPKIN_QUERY_NAMES_MAX_AGE`: Controls the value of the `max-age` header zipkin-server responds with on
 http requests for autocompleted values in the UI (service names for example). Defaults to 300 seconds.
* `ZIPKIN_QUERY_LOOKBACK`: How many milliseconds queries can look back from endTs; Defaults to 24 hours (two daily buckets: one for today and one for yesterday)
* `ZIPKIN_QUERY_ZIPKIN`: `-` disables the HTTP read endpoints under '/api/v2'. This also disables the
    UI, as it relies on the API. If your only goal is to restrict search, use `ZIPKIN_SEARCH_ENABLED` instead.
    Defaults to `zipkin`

### Configuration overrides

All configurations are stored in [YAML file](server-starter/src/main/resources/application.yml), and the configuration settings can be modified through environment variables prior to launching.

The contents in the configuration file are organized into three levels:
1. **Level 1**: Module name. This means that this module is active in running mode.
1. **Level 2**: Provider option list and provider selector. Available providers are listed here with a selector to indicate which one will actually take effect. If only one provider is listed, the `selector` is optional and can be omitted.
1. **Level 3**. Settings of the chosen provider.

Example:

```yaml
storage:
  selector: ${ZIPKIN_STORAGE:h2} # the h2 storage will actually be activated, while the mysql storage takes no effect
  h2:
    properties:
      jdbcUrl: ${ZIPKIN_STORAGE_H2_URL:jdbc:h2:mem:zipkin-db;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE}
      dataSource.user: ${ZIPKIN_STORAGE_H2_USER:sa}
    metadataQueryMaxSize: ${ZIPKIN_STORAGE_H2_QUERY_MAX_SIZE:5000}
    maxSizeOfBatchSql: ${ZIPKIN_STORAGE_MAX_SIZE_OF_BATCH_SQL:100}
    asyncBatchPersistentPoolSize: ${ZIPKIN_STORAGE_ASYNC_BATCH_PERSISTENT_POOL_SIZE:1}
  mysql:
    properties:
      jdbcUrl: ${ZIPKIN_JDBC_URL:"jdbc:mysql://localhost:3306/zipkin?rewriteBatchedStatements=true&allowMultiQueries=true"}
      driverClassName: org.mariadb.jdbc.Driver
      dataSource.user: ${ZIPKIN_DATA_SOURCE_USER:zipkin}
      dataSource.password: ${ZIPKIN_DATA_SOURCE_PASSWORD:zipkin}
  # other configurations
```

1. **`storage`** is the module.
1. **`selector`** selects one out of all providers listed below. The unselected ones take no effect as if they were deleted.
1. **`default`** is the default implementor of the core module.
1. `driver`, `url`, ... `metadataQueryMaxSize` are all setting items of the implementor.

At the same time, there are two types of modules: required and optional. The required modules provide the skeleton of the backend.
Even though their modular design supports pluggability, removing those modules does not serve any purpose. For optional modules, some of them have
a provider implementation called `none`, meaning that it only provides a shell with no actual logic, typically such as telemetry.
Setting `-` to the `selector` means that this whole module will be excluded at runtime.

The required modules are listed here:
1. **Core**. Provides the basic and major skeleton of all data analysis and stream dispatch.
1. **Cluster**. Manages multiple backend instances in a cluster, which could provide high throughput process capabilities. 
1. **Storage**. Makes the analysis result persistent.
1. **Receiver Zipkin**. Provide the basic configuration for all data collection protocols.

## UI
Zipkin has a web UI, automatically included in the exec jar, and is hosted by default on port 9411.

When the UI loads, it reads default configuration from the `/config.json` endpoint.

Attribute | Environment | Description
--- | --- | ---
restPort | ZIPKIN_QUERY_REST_PORT | The port for the UI interface defaults to the **ZIPKIN_SERVER_PORT** environment variable's port. If the value is greater than `0`, it will initiate a separate port to serve externally.
uiDefaultLookback | ZIPKIN_QUERY_UI_DEFAULT_LOOKBACK | Default duration in millis to look back when finding traces. Affects the "Start time" element in the UI. Defaults to 900000 (15 minutes in millis).
uiQueryLimit | ZIPKIN_QUERY_UI_QUERY_LIMIT | Default limit for Find Traces. Defaults to 10.
dependencyEnabled | ZIPKIN_QUERY_DEPENDENCY_ENABLED | If the Dependencies screen is enabled. Defaults to true.
dependencyLowErrorRate | ZIPKIN_QUERY_DEPENDENCY_LOW_ERROR_RATE | The rate of error calls on a dependency link that turns it yellow. Defaults to 0.5 (50%) set to >1 to disable.
dependencyHighErrorRate | ZIPKIN_QUERY_DEPENDENCY_HIGH_ERROR_RATE | The rate of error calls on a dependency link that turns it red. Defaults to 0.75 (75%) set to >1 to disable.
uiBasePath | ZIPKIN_QUERY_UI_BASE_PATH | path prefix placed into the <base> tag in the UI HTML; useful when running behind a reverse proxy. Default "/zipkin"

## Storage

### In-Memory Storage

Zipkin In-memory Storage uses an embedded H2 database for storage. By default, Zipkin utilizes this database.

Example usage:
```bash
$ java -jar zipkin.jar
```

Note: this storage component was primarily developed for testing and as a means to get Zipkin server
up and running quickly without external dependencies. It is not viable for high work loads. That
said, if you encounter out-of-memory errors, try increasing the heap size (-Xmx).

Exampled of doubling the amount of spans held in memory:
```bash
$ java -Xmx1G -jar zipkin.jar
```

### Cassandra Storage
Zipkin's Cassandra storage component supports Cassandra 3.11.3+
and applies when `ZIPKIN_STORAGE` is set to `cassandra`:

    * `ZIPKIN_STORAGE_CASSANDRA_KEYSPACE`: The keyspace to use. Defaults to "zipkin"
    * `ZIPKIN_STORAGE_CASSANDRA_CONTACT_POINTS`: Comma separated list of host addresses part of Cassandra cluster. You can also specify a custom port with 'host:port'. Defaults to localhost on port 9042.
    * `ZIPKIN_STORAGE_CASSANDRA_LOCAL_DC`: Name of the datacenter that will be considered "local" for load balancing. Defaults to "datacenter1"
    * `ZIPKIN_STORAGE_CASSANDRA_ENSURE_SCHEMA`: Ensuring cassandra has the latest schema. If enabled tries to execute scripts in the classpath prefixed with `cassandra-schema-cql3`. Defaults to true
    * `ZIPKIN_STORAGE_CASSANDRA_USERNAME` and `ZIPKIN_STORAGE_CASSANDRA_PASSWORD`: Cassandra authentication. Will throw an exception on startup if authentication fails. No default
    * `ZIPKIN_STORAGE_CASSANDRA_USE_SSL`: Requires `javax.net.ssl.trustStore` and `javax.net.ssl.trustStorePassword`, defaults to false.

The following are tuning parameters which may not concern all users:

    * `ZIPKIN_STORAGE_CASSANDRA_MAX_CONNECTIONS`: Max pooled connections per datacenter-local host. Defaults to 8

Example usage with Cassandra:
```bash
$ ZIPKIN_STORAGE=cassandra java -jar zipkin.jar
```

### Elasticsearch Storage
Zipkin's Elasticsearch storage component supports Elasticsearch versions 6-8.x, OpenSearch 1.1.0-1.3.10 and 2.4.0-2.8.0, and applies when `ZIPKIN_STORAGE` is set to `elasticsearch`.

The following apply when `ZIPKIN_STORAGE` is set to `elasticsearch`:

    * `ZIPKIN_STORAGE_ES_CLUSTER_NODES`: A comma separated list of elasticsearch base nodes to connect to ex. host:9200.
                  Defaults to "localhost:9200".
    * `ZIPKIN_STORAGE_ES_HTTP_PROTOCOL`: The protocol used when connecting to an Elasticsearch cluster. Defaults to "http".
    * `ZIPKIN_NAMESPACE`: All namespaces of Index are distinguished from other indexes by prefixes. Defaults to "zipkin".
    * `ZIPKIN_STORAGE_ES_CONNECT_TIMEOUT`: Connect timeout of ElasticSearch client. Defaults to 3000 (3 seconds)
    * `ZIPKIN_STORAGE_ES_SOCKET_TIMEOUT`: Socket timeout of ElasticSearch client. Defaults to 30000 (30 seconds)
    * `ZIPKIN_STORAGE_ES_RESPONSE_TIMEOUT`: the response timeout of ElasticSearch client (Armeria under the hood), set to 0 to disable response. Defaults to 15000 (15 seconds)
    * `ZIPKIN_STORAGE_ES_NUM_HTTP_CLIENT_THREAD`: The number of threads for the underlying HTTP client to perform socket I/O.
                                                  If the value is <= 0, the number of available processors will be used. Defaults to 0.
    * `ZIPKIN_ES_USER` and `ZIPKIN_ES_PASSWORD`: Elasticsearch basic authentication, which defaults to empty string.
                                                 Use when X-Pack security (formerly Shield) is in place.
    * `ZIPKIN_STORAGE_ES_SSL_JKS_PATH`: The path to the Java keystore file containing an Elasticsearch SSL certificate for use with HTTPS. Defaults to "".
    * `ZIPKIN_STORAGE_ES_SSL_JKS_PASS`: The password for the Java keystore file containing an Elasticsearch SSL certificate for use with HTTPS. Defaults to "".
    * `ZIPKIN_ES_SECRETS_MANAGEMENT_FILE`: Secrets management file in the properties format includes the username, password, which are managed by 3rd party tool. Defaults to "".
    * `ZIPKIN_STORAGE_DAY_STEP`: Represent the number of days in the one minute/hour/day index. Defaults to 1.
    * `ZIPKIN_STORAGE_ES_INDEX_SHARDS_NUMBER`: The number of shards to split the index into. Each shard and its replicas
                                               are assigned to a machine in the cluster. Increasing the number of shards
                                               and machines in the cluster will improve read and write performance. Number
                                               of shards cannot be changed for existing indices, but new daily indices
                                               will pick up changes to the setting. Defaults to 1.
    * `ZIPKIN_STORAGE_ES_INDEX_REPLICAS_NUMBER`: The number of replica copies of each shard in the index. Each shard and
                                                 its replicas are assigned to a machine in the cluster. Increasing the
                                                 number of replicas and machines in the cluster will improve read
                                                 performance, but not write performance. Number of replicas can be changed
                                                 for existing indices. Defaults to 1. It is highly discouraged to set this
                                                 to 0 as it would mean a machine failure results in data loss. Defaults to 1.
    * `ZIPKIN_STORAGE_ES_SPECIFIC_INDEX_SETTINGS`: Specify the settings for specify index individually.
                                                   If configured, this setting has the highest priority and overrides the generic settings.
    * `ZIPKIN_STORAGE_ES_SUPER_DATASET_DAY_STEP`: Represent the number of days in the zipkin span record index, 
                                                  the default value is the same as dayStep when the value is less than 0
    * `ZIPKIN_STORAGE_ES_SUPER_DATASET_INDEX_SHARDS_FACTOR`: This factor provides more shards for the zipkin span record, 
                                                             shards number = indexShardsNumber * superDatasetIndexShardsFactor.
                                                             Defaults to 5.
    * `ZIPKIN_STORAGE_ES_SUPER_DATASET_INDEX_REPLICAS_NUMBER`: Represent the replicas number in the zipkin span record index, Defaults to 0.
    * `ZIPKIN_STORAGE_ES_INDEX_TEMPLATE_ORDER`: The order of the index template. Defaults to 0.
    * `ZIPKIN_STORAGE_ES_BULK_ACTIONS`: The number of requests to send to Elasticsearch in a single bulk request. Defaults to 5000.
    * `ZIPKIN_STORAGE_ES_BATCH_OF_BYTES`: The number of bytes to send to Elasticsearch in a single bulk request. Defaults to 10485760 (10MB).
    * `ZIPKIN_STORAGE_ES_FLUSH_INTERVAL`: The number of second to wait between bulk requests to Elasticsearch. Defaults to 5.
    * `ZIPKIN_STORAGE_ES_CONCURRENT_REQUESTS`: The number of concurrent requests to Elasticsearch. Defaults to 2.
    * `ZIPKIN_STORAGE_ES_LOGIC_SHARDING`: Enable shard metrics and records indices into multi-physical indices, 
                                          one index template per metric/meter aggregation function or record. Defaults to false.
    * `ZIPKIN_STORAGE_ES_ENABLE_CUSTOM_ROUTING`: Custom routing can reduce the impact of searches. Instead of having to fan out 
                                          a search request to all the shards in an index, the request can be sent to 
                                          just the shard that matches the specific routing value (or values). Defaults to false.

Example usage:

To connect normally:
```bash
$ ZIPKIN_STORAGE=elasticsearch ZIPKIN_STORAGE_ES_CLUSTER_NODES=myhost:9200 java -jar zipkin.jar
```

#### ElasticSearch With Https SSL Encrypting communications.

Example:

```yaml
storage:
  selector: ${ZIPKIN_STORAGE:elasticsearch}
  elasticsearch:
    namespace: ${ZIPKIN_NAMESPACE:"zipkin"}
    user: ${ZIPKIN_ES_USER:""} # User needs to be set when Http Basic authentication is enabled
    password: ${ZIPKIN_ES_PASSWORD:""} # Password to be set when Http Basic authentication is enabled
    clusterNodes: ${ZIPKIN_STORAGE_ES_CLUSTER_NODES:localhost:443}
    trustStorePath: ${ZIPKIN_STORAGE_ES_SSL_JKS_PATH:"../es_keystore.jks"}
    trustStorePass: ${ZIPKIN_STORAGE_ES_SSL_JKS_PASS:""}
    protocol: ${SW_STORAGE_ES_HTTP_PROTOCOL:"https"}
    ...
```
- File at `trustStorePath` is being monitored. Once it is changed, the ElasticSearch client will reconnect.
- `trustStorePass` could be changed in the runtime through [**Secrets Management File Of ElasticSearch Authentication**](#secrets-management-file-of-elasticsearch-authentication).

#### Secrets Management File Of ElasticSearch Authentication
The value of `secretsManagementFile` should point to the secrets management file absolute path.
The file includes the username, password, and JKS password of the ElasticSearch server in the properties format.
```properties
user=xxx
password=yyy
trustStorePass=zzz
```

The major difference between using `user, password, trustStorePass` configs in the `application.yaml` file is that the **Secrets Management File** is being watched by the Zipkin server.
Once it is changed manually or through a 3rd party tool, such as [Vault](https://github.com/hashicorp/vault),
the storage provider will use the new username, password, and JKS password to establish the connection and close the old one. If the information exists in the file,
the `user/password` will be overridden.

#### Daily Index Step
Daily index step(`storage/elasticsearch/dayStep`, default 1) represents the index creation period. In this period, metrics for several days (dayStep value) are saved.

In most cases, users don't need to change the value manually, as SkyWalking is designed to observe large-scale distributed systems.
But in some cases, users may want to set a long TTL value, such as more than 60 days. However, their ElasticSearch cluster may not be powerful enough due to low traffic in the production environment.
This value could be increased to 5 (or more) if users could ensure a single index could support the metrics and traces for these days (5 in this case).

For example, if dayStep == 11,
1. Data in [2000-01-01, 2000-01-11] will be merged into the index-20000101.
1. Data in [2000-01-12, 2000-01-22] will be merged into the index-20000112.

`storage/elasticsearch/superDatasetDayStep` overrides the `storage/elasticsearch/dayStep` if the value is positive. This would affect the zipkin span entity.

NOTE: TTL deletion would be affected by these steps. You should set an extra dayStep in your TTL. For example, if you want to have TTL == 30 days and dayStep == 10, you are recommended to set TTL = 40.

#### Index Settings
The following settings control the number of shards and replicas for new and existing index templates. The update only got applied after OAP reboots.
```yaml
storage:
  elasticsearch:
    # ......
    indexShardsNumber: ${ZIPKIN_STORAGE_ES_INDEX_SHARDS_NUMBER:1}
    indexReplicasNumber: ${ZIPKIN_STORAGE_ES_INDEX_REPLICAS_NUMBER:1}
    specificIndexSettings: ${ZIPKIN_STORAGE_ES_SPECIFIC_INDEX_SETTINGS:""}
    superDatasetIndexShardsFactor: ${ZIPKIN_STORAGE_ES_SUPER_DATASET_INDEX_SHARDS_FACTOR:5}
    superDatasetIndexReplicasNumber: ${ZIPKIN_STORAGE_ES_SUPER_DATASET_INDEX_REPLICAS_NUMBER:0}
```
The following table shows the relationship between those config items and Elasticsearch `index number_of_shards/number_of_replicas`.
And also you can [specify the settings for each index individually.](#specify-settings-for-each-elasticsearch-index-individually)

| index                                | number_of_shards | number_of_replicas   | description |
|--------------------------------------|------------------|----------------------|-------------|
| zipkin_metrics-all-`${day-format}`       | indexShardsNumber | indexReplicasNumber  | All metrics/meters generated by zipkin spans, and metadata of service |
| zipkin_zipkin_span-`${day-format}`       | indexShardsNumber * superDatasetIndexShardsFactor | superDatasetIndexReplicasNumber  | Zipkin trace spans |

##### Advanced Configurations For Elasticsearch Index
You can add advanced configurations in `JSON` format to set `ElasticSearch index settings` by following [ElasticSearch doc](https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules.html)

For example, set [translog](https://www.elastic.co/guide/en/elasticsearch/reference/master/index-modules-translog.html) settings:

```yaml
storage:
  elasticsearch:
    # ......
    advanced: ${ZIPKIN_STORAGE_ES_ADVANCED:"{\"index.translog.durability\":\"request\",\"index.translog.sync_interval\":\"5s\"}"}
```

##### Specify Settings For Each Elasticsearch Index Individually
You can specify the settings for one or more indexes individually by using `SW_STORAGE_ES_SPECIFIC_INDEX_SETTINGS`.

**NOTE:**
Supported settings:
- number_of_shards
- number_of_replicas

**NOTE:** These settings have the highest priority and will override the existing
generic settings mentioned in [index settings doc](#index-settings).

The settings are in `JSON` format. The index name here is logic entity name, which should exclude the `${ZIPKIN_NAMESPACE}` which is `zipkin` by default, e.g.
```json
{
  "metrics-all":{
    "number_of_shards":"3",
    "number_of_replicas":"2"
  },
  "segment":{
    "number_of_shards":"6",
    "number_of_replicas":"1"
  }
}
```

This configuration in the YAML file is like this,
```yaml
storage:
  elasticsearch:
    # ......
    specificIndexSettings: ${ZIPKIN_STORAGE_ES_SPECIFIC_INDEX_SETTINGS:"{\"metrics-all\":{\"number_of_shards\":\"3\",\"number_of_replicas\":\"2\"},\"segment\":{\"number_of_shards\":\"6\",\"number_of_replicas\":\"1\"}}"}
```

#### Recommended ElasticSearch server-side configurations
You could add the following configuration to `elasticsearch.yml`, and set the value based on your environment.

```yml
# In tracing scenario, consider to set more than this at least.
thread_pool.index.queue_size: 1000 # Only suitable for ElasticSearch 6
thread_pool.write.queue_size: 1000 # Suitable for ElasticSearch 6 and 7

# When you face a query error on the traces page, remember to check this.
index.max_result_window: 1000000
```

We strongly recommend that you read more about these configurations from ElasticSearch's official documentation since they directly impact the performance of ElasticSearch.

### BanyanDB storage components

[BanyanDB](https://github.com/apache/skywalking-banyandb) is a dedicated storage implementation developed by the SkyWalking Team and the community.
Currently, BanyanDB is still in the PoC stage and it is not recommended to use it in a production environment.

The following apply when `ZIPKIN_STORAGE` is set to `banyandb`:
    
    * `ZIPKIN_STORAGE_BANYANDB_HOST`: The host of BanyanDB. Defaults to `127.0.0.1`.
    * `ZIPKIN_STORAGE_BANYANDB_PORT`: The port of BanyanDB. Defaults to `17912`.
    * `ZIPKIN_STORAGE_BANYANDB_MAX_BULK_SIZE`: The max bulk size of BanyanDB. Defaults to `5000`.
    * `ZIPKIN_STORAGE_BANYANDB_FLUSH_INTERVAL`: The flush interval of BanyanDB. Defaults to `15`.
    * `ZIPKIN_STORAGE_BANYANDB_METRICS_SHARDS_NUMBER`: The number of shards of metrics index. Defaults to `1`.
    * `ZIPKIN_STORAGE_BANYANDB_SUPERDATASET_SHARDS_FACTOR`: The shards factor of zipkin span record index. Defaults to `2`.
    * `ZIPKIN_STORAGE_BANYANDB_CONCURRENT_WRITE_THREADS`: The number of concurrent write threads. Defaults to `15`.
    * `ZIPKIN_STORAGE_BANYANDB_BLOCK_INTERVAL_HOURS`: The block interval hours of BanyanDB. Defaults to `24` hour.
    * `ZIPKIN_STORAGE_BANYANDB_SEGMENT_INTERVAL_DAYS`: The segment interval days of BanyanDB. Defaults to `1` days.
    * `ZIPKIN_STORAGE_BANYANDB_SUPER_DATASET_BLOCK_INTERVAL_HOURS`: The zipkin span record block interval hours of BanyanDB. Defaults to `4` hour.
    * `ZIPKIN_STORAGE_BANYANDB_SUPER_DATASET_SEGMENT_INTERVAL_DAYS`: The zipkin span record segment interval days of BanyanDB. Defaults to `1` days.
    * `ZIPKIN_STORAGE_BANYANDB_SPECIFIC_GROUP_SETTINGS`: The specific group settings of BanyanDB. ex, `{"group1": {"blockIntervalHours": 4, "segmentIntervalDays": 1}}`. Defaults to "".

Example usage:

```bash
$ ZIPKIN_STORAGE=banyandb java -jar zipkin.jar
```

### Legacy (v1) storage components
The following components are no longer encouraged, but exist to help aid
transition to supported ones. These are indicated as "v1" as they use
data layouts based on Zipkin's V1 Thrift model, as opposed to the
simpler v2 data model currently used.

#### MySQL/PostgreSQL Storage
Zipkin's MySQL/PostgreSQL component is tested against MySQL 5.7, PostgreSQL 9 and applies when `ZIPKIN_STORAGE` is set to `mysql`/`postgresql`:

    * `ZIPKIN_JDBC_URL`: The connection string to MySQL, ex. `jdbc:mysql://host/dbname`. 
                         Defaults to `jdbc:mysql://localhost:3306/zipkin?rewriteBatchedStatements=true&allowMultiQueries=true` when using mysql 
                         and `jdbc:postgresql://localhost:5432/zipkin` when using postgresql.
    * `ZIPKIN_DATA_SOURCE_USER` and `ZIPKIN_DATA_SOURCE_PASSWORD`: MySQL authentication, which defaults to `zipkin` and `zipkin`.
    * `ZIPKIN_DATA_SOURCE_CACHE_PREP_STMTS`: Whether prepared statements should be cached or not. Defaults to `true`.
    * `ZIPKIN_DATA_SOURCE_PREP_STMT_CACHE_SQL_SIZE`: The number of prepared statements that the driver will cache per connection. Defaults to 250.
    * `ZIPKIN_DATA_SOURCE_PREP_STMT_CACHE_SQL_LIMIT`: The number of queries that can be cached in an LRU cache, to limit the memory cost of caching prepared statements. Defaults to 2048.
    * `ZIPKIN_DATA_SOURCE_USE_SERVER_PREP_STMTS`: Whether the driver should use a per-connection cache of prepared statements. Defaults to `true`.
    * `ZIPKIN_STORAGE_MAX_SIZE_OF_BATCH_SQL`: Maximum number of statements to batch in one go. Defaults to 2000.
    * `ZIPKIN_STORAGE_ASYNC_BATCH_PERSISTENT_POOL_SIZE`: Maximum number of threads writing to MySQL. Defaults to 4.

Note: This module is not recommended for production usage. 

Example usage:

```bash
$ ZIPKIN_STORAGE=mysql ZIPKIN_DATA_SOURCE_USER=zipkin ZIPKIN_DATA_SOURCE_PASSWORD=zipkin java -jar zipkin.jar
```

## Collector

### HTTP Collector
The HTTP collector is enabled by default. It accepts spans via `POST /api/v1/spans` and `POST /api/v2/spans`.
The HTTP collector supports the following configuration:

Environment Variable | Default | Description
--- | --- | ---
`ZIPKIN_RECEIVER_ZIPKIN_HTTP` | `default` | `-` disables the HTTP collector.

### Scribe (Legacy) Collector
A collector supporting Scribe is enabled when `ZIPKIN_RECEIVER_ZIPKIN_SCRIBE=default`. New
sites are discouraged from using this collector as Scribe is an archived
technology.

Environment Variable | Default | Description
--- | --- | ---
`ZIPKIN_COLLECTOR_PORT` | `9410` | The port to listen for thrift RPC scribe requests.
`ZIPKIN_SCRIBE_CATEGORY` | `zipkin` | Category zipkin spans will be consumed from.


### ActiveMQ Collector
The ActiveMQ Collector is enabled when `ZIPKIN_RECEIVER_ZIPKIN_ACTIVEMQ` is set to `default`. The following settings apply in this case.

Environment Variable | Default | Description
--- | --- | ---
`ZIPKIN_RECEIVER_ZIPKIN_ACTIVEMQ` | `-` | `default` enable the ActiveMQ collector.
`ZIPKIN_ACTIVEMQ_URL` | `` | [Connection URL](https://activemq.apache.org/uri-protocols) to the ActiveMQ broker, ex. `tcp://localhost:61616` or `failover:(tcp://localhost:61616,tcp://remotehost:61616)`
`ZIPKIN_ACTIVEMQ_QUEUE` | `zipkin` | Queue from which to collect span messages.
`ZIPKIN_ACTIVEMQ_CLIENT_ID_PREFIX` | `zipkin` | Client ID prefix for queue consumers. Defaults to `zipkin`
`ZIPKIN_ACTIVEMQ_CONCURRENCY` | `1` | Number of concurrent span consumers.
`ZIPKIN_ACTIVEMQ_USERNAME` | `` | Optional username to connect to the broker
`ZIPKIN_ACTIVEMQ_PASSWORD`| `` | Optional password to connect to the broker

Example usage:

```bash
$ ZIPKIN_RECEIVER_ZIPKIN_ACTIVEMQ=default ZIPKIN_ACTIVEMQ_URL=tcp://localhost:61616 java -jar zipkin.jar
```

### Kafka Collector
The Kafka collector is enabled when `ZIPKIN_RECEIVER_ZIPKIN_KAFKA` is set to `default`. 

Environment Variable | Default | Description
--- | --- | ---
`ZIPKIN_RECEIVER_ZIPKIN_KAFKA` | `-` | `default` enable the Kafka collector.
`ZIPKIN_KAFKA_SERVERS` | `localhost:9092` | Comma-separated list of brokers, ex. `127.0.0.1:9092`.
`ZIPKIN_KAFKA_GROUP_ID` | `zipkin` | The consumer group this process is consuming on behalf of.
`ZIPKIN_KAFKA_TOPIC` | `zipkin` | Comma-separated list of topics that zipkin spans will be consumed from.
`ZIPKIN_KAFKA_CONSUMER_CONFIG` | `{\"auto.offset.reset\":\"earliest\",\"enable.auto.commit\":true}` | Kafka consumer config, JSON format as Properties. If it contains the same key with above, would override.
`ZIPKIN_KAFKA_CONSUMERS` | `1` | Number of consumers reading from the topic.
`ZIPKIN_KAFKA_HANDLER_THREAD_POOL_SIZE` | `-1` | The size of the thread pool that the Kafka consumer would use to schedule data processing. If <= 0, the default value is the number of processors available to the Java virtual machine.
`ZIPKIN_KAFKA_HANDLER_THREAD_POOL_QUEUE_SIZE` | `-1` | The size of the queue that the Kafka consumer would use to buffer data to be processed. 

Example usage:

```bash
$ ZIPKIN_RECEIVER_ZIPKIN_KAFKA=default ZIPKIN_KAFKA_SERVERS=127.0.0.1:9092 java -jar zipkin.jar
```

#### Other Kafka consumer properties
You may need to set other
[Kafka consumer properties](https://kafka.apache.org/documentation/#newconsumerconfigs), in
addition to the ones with explicit properties defined by the collector. In this case, you need to
prefix that property name with `zipkin.collector.kafka.overrides` and pass it as a system property
argument.

For example, to override `auto.offset.reset`, you can set environment variable
`ZIPKIN_KAFKA_CONSUMER_CONFIG={"auto.offset.reset":"earliest"}`:

```bash
$ ZIPKIN_RECEIVER_ZIPKIN_KAFKA=default ZIPKIN_KAFKA_SERVERS=127.0.0.1:9092 \
    ZIPKIN_KAFKA_CONSUMER_CONFIG={"auto.offset.reset":"latest"} \
    java -jar zipkin.jar
```

#### Detailed examples

Example targeting Kafka running in Docker:

```bash
$ export ZIPKIN_KAFKA_SERVERS=$(docker-machine ip `docker-machine active`)
# Run Kafka in the background
$ docker run -d -p 9092:9092 \
    --env ADVERTISED_HOST=ZIPKIN_KAFKA_SERVERS \
    --env AUTO_CREATE_TOPICS=true \
    spotify/kafka
# Start the zipkin server, which reads $ZIPKIN_KAFKA_SERVERS
$ ZIPKIN_RECEIVER_ZIPKIN_KAFKA=default java -jar zipkin.jar
```

Multiple bootstrap servers:

```bash
$ ZIPKIN_RECEIVER_ZIPKIN_KAFKA=default ZIPKIN_KAFKA_SERVERS=broker1.local:9092,broker2.local:9092 \
    java -jar zipkin.jar
```

Alternate topic name(s):

```bash
$ ZIPKIN_RECEIVER_ZIPKIN_KAFKA=default ZIPKIN_KAFKA_SERVERS=127.0.0.1:9092 ZIPKIN_KAFKA_TOPIC=zapkin,zipken \
    java -jar zipkin.jar
```

### RabbitMQ collector
The RabbitMQ collector will be enabled when the `ZIPKIN_RECEIVER_ZIPKIN_RABBITMQ` is set to `default`.

Environment Variable | Default | Description
--- | --- | ---
`ZIPKIN_RECEIVER_ZIPKIN_RABBITMQ` | `-` | `default` enable the RabbitMQ collector.
`ZIPKIN_RECEIVER_RABBIT_ADDRESSES` | `` | Comma-separated list of addresses to which the client will connect.
`ZIPKIN_RECEIVER_RABBIT_CONCURRENCY` | `1` | Number of concurrent consumers.
`ZIPKIN_RECEIVER_RABBIT_CONNECTION_TIMEOUT` | `60000` | TCP connection timeout in milliseconds.
`ZIPKIN_RECEIVER_RABBIT_USER` | `guest` | Username to use when authenticating to the server.
`ZIPKIN_RECEIVER_RABBIT_PASSWORD` | `guest` | Password to use when authenticating to the server.
`ZIPKIN_RECEIVER_RABBIT_QUEUE` | `zipkin` | Name of the queue to listen for spans.
`ZIPKIN_RECEIVER_RABBIT_VIRTUAL_HOST` | `/` | Virtual host to use when connecting to the RabbitMQ.
`ZIPKIN_RECEIVER_RABBIT_USE_SSL` | `false` | Whether to use SSL when connecting.
`ZIPKIN_RECEIVER_RABBIT_URI` | `` | The RabbitMQ URI to connect to. When set, it overrides all other RabbitMQ connection properties.

Example usage:

```bash
$ ZIPKIN_RECEIVER_ZIPKIN_RABBITMQ=default ZIPKIN_RECEIVER_RABBIT_ADDRESSES=127.0.0.1:5672 java -jar zipkin.jar
```

### gRPC Collector (Experimental)
You can enable a gRPC span collector endpoint by setting `ZIPKIN_RECEIVER_ZIPKIN_GRPC=default`. The
`zipkin.proto3.SpanService/Report` [endpoint](https://github.com/openzipkin/zipkin-api/blob/7692ca7be4dc3be9225db550d60c4d30e6e9ec59/zipkin.proto#L232) will run on the same port as normal HTTP (9411).


Example usage:

```bash
$ ZIPKIN_RECEIVER_ZIPKIN_GRPC=true java -jar zipkin.jar
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

## TLS/SSL
Zipkin-server can be made to run with TLS if needed:

```bash
# assuming you generate the key like this
openssl genpkey -algorithm RSA -out private-key.pem
openssl req -new -key private-key.pem -out certificate-request.csr
openssl x509 -req -in certificate-request.csr -signkey private-key.pem -out certificate.pem

ZIPKIN_SERVER_SSL_ENABLED=true ZIPKIN_SERVER_SSL_KEY_PATH=./private-key.pem ZIPKIN_SERVER_SSL_CERT_CHAIN_PATH=./certificate.pem java -jar zipkin.jar 
```

## Running with Docker
Released versions of zipkin-server are published to Docker Hub as `openzipkin/zipkin`.
See [docker](./../docker) for details.

## Building locally

To build and run the server from the currently checked out source, enter the following.
```bash
# Init submodules
git submodule update --init --recursive
# Build the server and also make its dependencies
$ ./mvnw -T1C -q --batch-mode -DskipTests -Dcheckstyle.skip=true --also-make -pl :zipkin-server clean package
# Run the server
$ java -jar ./zipkin-server/server-starter/target/zipkin-server-*exec.jar
# or Run the slim server
$ java -jar ./zipkin-server/server-starter/target/zipkin-server-*slim.jar
```
