# storage-cassandra

*This module is experimental. Please help test this, but do not use it in production.*

This CQL-based Cassandra 3.9+ storage component, built upon the Zipkin2 API.

`CassandraSpanStore.getDependencies()` returns pre-aggregated dependency links (ex via [zipkin-dependencies](https://github.com/openzipkin/zipkin-dependencies)).

The implementation uses the [Datastax Java Driver 3.1.x](https://github.com/datastax/java-driver).

`zipkin2.storage.cassandra.CassandraStorage.Builder` includes defaults that will operate against a local Cassandra installation.

## Logging
Queries are logged to the category "com.datastax.driver.core.QueryLogger" when debug or trace is
enabled via SLF4J. Trace level includes bound values.

See [Logging Query Latencies](http://docs.datastax.com/en/developer/java-driver/3.0/supplemental/manual/logging/#logging-query-latencies) for more details.

## Testing
This module conditionally runs integration tests against a local Cassandra instance.

This starts a docker container or attempts to re-use an existing cassandra node running on localhost.

If you run tests via Maven or otherwise when Cassandra is not running,
you'll notice tests are silently skipped.
```
Results :

Tests run: 62, Failures: 0, Errors: 0, Skipped: 48
```

This behaviour is intentional: We don't want to burden developers with
installing and running all storage options to test unrelated change.
That said, all integration tests run on pull request via Travis.

### Running a single test

To run a single integration test, use the following syntax:

```bash
$ ./mvnw -Dit.test='ITCassandraStorage$SpanStoreTest#getTraces_duration' -pl zipkin-storage/zipkin2_cassandra clean verify
```

## Tuning
This component is tuned to help reduce the size of indexes needed to
perform query operations. The most important aspects are described below.
See [CassandraStorage](src/main/java/zipkin2/storage/cassandra/CassandraStorage.java) for details.

### Trace indexing
Indexing in CQL is simplified by SASI, for example, reducing the number
of tables from 7 down to 4. SASI also moves some write-amplification from
CassandraSpanConsumer into C*.

CassandraSpanConsumer directly writes to the tables `span`,
`trace_by_service_span` and `span_by_service`. The latter two
amplify writes by a factor of the distinct service names in a span.
Other amplification happens internally to C*, visible in the increase
write latency (although write latency remains performant at single digit
milliseconds).

* A SASI index on its `annotation_query` column permits full-text searches against annotations.
* A SASI index on the `duration` column.
* A SASI index on the `l_service` column (the local_service name), which is used in conjunction with annotation_query searches.

Note: annotations with values longer than 256 characters
are not written to the `annotation_query` SASI, as they aren't intended
for use in user queries.

The `trace_by_service_span` index is only used by query apis, and notably supports millisecond
resolution duration. In other words, query inputs are rounded up to the next millisecond. For
example, a call to GET /api/v2/traces?minDuration=12345 will returns traces who include a span that
has at least 13 millisecond duration. This resolution only affects the query: original duration data
remains at microsecond granularity. Meanwhile, write performance is dramatically better than writing
discrete values, via fewer distinct writes.

### Time-To_live
Time-To-Live is default now at the table level. It can not be overridden in write requests.

There's a different default TTL for trace data and indexes, 7 days vs 3 days respectively. The impact is that you can
retrieve a trace by ID for up to 7 days, but you can only search the last 3 days of traces (ex by service name).

### Compaction
Time-series data is compacted using TimeWindowCompactionStrategy, a known improved over DateTieredCompactionStrategy. Data is
optimised for queries within a single day. The penalty of reading multiple days is small, a few disk seeks, compared to the
otherwise overhead of reading a significantly larger amount of data.

### Benchmarking
Benchmarking the new datamodel demonstrates a significant performance improvement on reads. How much of this translates to the
Zipkin UI is hard to tell due to the complexity of CassandraSpanConsumer and how searches are possible. Benchmarking stress
profiles are found in traces-stress.yaml and trace_by_service_span-stress.yaml and span_by_service-stress.yaml.
