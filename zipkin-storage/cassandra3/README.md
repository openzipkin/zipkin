# storage-cassandra3

*This module is experimental. Please help test this, but do not use it in production.*

This CQL-based Cassandra 3.9+ storage component includes a `GuavaSpanStore` and `GuavaSpanConsumer`.
`GuavaSpanStore.getDependencies()` returns pre-aggregated dependency links (ex via [zipkin-dependencies](https://github.com/openzipkin/zipkin-dependencies)).

The implementation uses the [Datastax Java Driver 3.x](https://github.com/datastax/java-driver).

`zipkin.storage.cassandra3.Cassandra3Storage.Builder` includes defaults that will operate against a local Cassandra installation.

## Logging
Queries are logged to the category "com.datastax.driver.core.QueryLogger" when debug or trace is
enabled via SLF4J. Trace level includes bound values.

See [Logging Query Latencies](http://docs.datastax.com/en/developer/java-driver/3.0/supplemental/manual/logging/#logging-query-latencies) for more details.

## Testing
This module conditionally runs integration tests against a local Cassandra instance.

Tests are configured to automatically access Cassandra started with its defaults.
To ensure tests execute, download a Cassandra 3.9+ distribution, extract it, and run `bin/cassandra`.

If you run tests via Maven or otherwise when Cassandra is not running,
you'll notice tests are silently skipped.
```
Results :

Tests run: 62, Failures: 0, Errors: 0, Skipped: 48
```

This behaviour is intentional: We don't want to burden developers with
installing and running all storage options to test unrelated change.
That said, all integration tests run on pull request via Travis.

## Tuning
This component is tuned to help reduce the size of indexes needed to
perform query operations. The most important aspects are described below.
See [Cassandra3Storage](src/main/java/zipkin/storage/cassandra3/Cassandra3Storage.java) for details.

### Trace indexing
Indexing in CQL is simplified by SASI, for example, reducing the number
of tables from 7 down to 4. SASI also moves some write-amplification from
CassandraSpanConsumer into C*.

CassandraSpanConsumer directly writes to the tables `traces`,
`trace_by_service_span` and `span_name_by_service`. The latter two
amplify writes by a factor of the distinct service names in a span.
Other amplification happens internally to C*, visible in the increase
write latency (although write latency remains performant at single digit
milliseconds).

* A SASI index on its 'all_annotations' column permits full-text searches against annotations.
* A SASI index on the 'duration' column.

Note: [Core annotations](../../zipkin/src/main/java/zipkin/Constants.java#L186-L188),
ex "sr", non-string annotations, and values longer than 256 characters
are not written to the `all_annotations` SASI, as they aren't intended
for use in user queries.

### Time-To_live
Time-To-Live is default now at the table level. It can not be overridden in write requests.

There's a different default TTL for trace data and indexes, 7 days vs 3 days respectively. The impact is that you can
retrieve a trace by ID for up to 7 days, but you can only search the last 3 days of traces (ex by service name).

### Compaction
Time-series data is compacted using TimeWindowCompactionStrategy, a known improved over DateTieredCompactionStrategy. Data is
optimised for queries with a single day. The penalty of reading multiple days is small, a few disk seeks, compared to the
otherwise overhead of reading a significantly larger amount of data.

### Benchmarking
Benchmarking the new datamodel demonstrates a significant performance improvement on reads. How much of this translates to te
Zipkin UI is hard to tell due to the complexity of CassandraSpanConsumer and how searches are possible. Benchmarking stress
profiles are found in traces-stress.yaml and trace_by_service_span-stress.yaml.
