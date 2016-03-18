# zipkin-cassandra

## Service Configuration

"cassandra" is a storage backend to the following services
* [zipkin-collector-service](https://github.com/openzipkin/zipkin/blob/master/zipkin-collector-service/README.md)
* [zipkin-query-service](https://github.com/openzipkin/zipkin/blob/master/zipkin-query-service/README.md)

Here are the Cassandra-specific environment variables:

   * `CASSANDRA_KEYSPACE`: The keyspace to use. Defaults to "zipkin".
   * `CASSANDRA_ENSURE_SCHEMA`: Ensuring that schema exists, if enabled tries to execute script /zipkin-cassandra-core/resources/cassandra-schema-cql3.txt. Defaults to true
   * `CASSANDRA_USERNAME` and `CASSANDRA_PASSWORD`: Cassandra authentication. Will throw an exception on startup if authentication fails. No default
   * `CASSANDRA_CONTACT_POINTS`: Comma separated list of hosts / ip addresses part of Cassandra cluster. Defaults to localhost
   * `CASSANDRA_LOCAL_DC`: Name of the datacenter that will be considered "local" for latency load balancing. When unset, load-balancing is round-robin.
   * `CASSANDRA_MAX_CONNECTIONS`: Max pooled connections per datacenter-local host. Defaults to 8
   * `CASSANDRA_SPAN_TTL`: Time-to-live in seconds for span data. Defaults to 604800 (7 days)
   * `CASSANDRA_INDEX_TTL`: Time-to-live in seconds for index data. Defaults to 259200 (3 days)

Example usage:

```bash
$ CASSANDRA_USERNAME=user CASSANDRA_PASSWORD=pass QUERY_LOG_LEVEL=ERROR ./bin/query cassandra
```
