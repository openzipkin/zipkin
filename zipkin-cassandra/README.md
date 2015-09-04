# zipkin-cassandra

## Service Configuration

"cassandra" is a storage backend to the following services
* [zipkin-collector-service](https://github.com/openzipkin/zipkin/blob/master/zipkin-collector-service/README.md)
* [zipkin-query-service](https://github.com/openzipkin/zipkin/blob/master/zipkin-query-service/README.md)

These services apply configuration through environment variables:

   * `CASSANDRA_USERNAME` and `CASSANDRA_PASSWORD`: Cassandra authentication. Will throw an exception on startup if authentication fails
   * `CASSANDRA_CONTACT_POINTS`: Comma separated list of hosts / ip addresses part of Cassandra cluster
   * `COLLECTOR_SAMPLE_RATE`: Sample rate. Double value between 0.0 (nothing ends up in back-end store) and 1.0 (everything ends up in back-end store)
   * `COLLECTOR_QUEUE_NUM_WORKERS`: Number of worker threads that pick spans from internal bounded queue and write to back-end store
   * `COLLECTOR_QUEUE_MAX_SIZE`: Internal queue size. If queue runs full offered spans are dropped. 
   * `COLLECTOR_PORT`: Collector port
   * `COLLECTOR_ADMIN_PORT`: Collector admin port. Port for admin http service. Admin service provides operational metrics for zipkin-collector-service.
   * `COLLECTOR_LOG_LEVEL`: Collector log level. Valid values: OFF, FATAL, CRITICAL, ERROR, WARNING, INFO, DEBUG, TRACE, ALL

For default values see:
* [zipkin-collector-service/config/collector-cassandra.scala](https://github.com/openzipkin/zipkin/blob/master/zipkin-collector-service/config/collector-cassandra.scala).
* [zipkin-query-service/config/query-cassandra.scala](https://github.com/openzipkin/zipkin/blob/master/zipkin-query-service/config/query-cassandra.scala).

Example usage:

```bash
$ CASSANDRA_USERNAME=user CASSANDRA_PASSWORD=pass COLLECTOR_LOG_LEVEL=ERROR ./bin/collector cassandra
```
