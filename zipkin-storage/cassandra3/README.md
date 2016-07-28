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

Indexing in CQL is simplified by using both MATERIALIZED VIEWs and SASI.
This has reduced the number of tables from 7 down to 2. This also reduces the write-amplification in CassandraSpanConsumer.
This write amplification now happens internally to C*, and is visible in the increase write latency (although write latency
remains performant at single digit milliseconds).
A SASI index on its 'all_annotations' column permits full-text searches against annotations.
A SASI index on the 'duration' column.
A materialized view of the 'trace_by_service_span' table exists as 'trace_by_service'.

[Core annotations](../zipkin/src/main/java/zipkin/Constants.java#L184),
ex "sr", and non-string annotations, are not written to the `all_annotations` SASI, as they aren't intended for use in user queries.

### Time-To_live
Time-To-Live is default now at the table level. It can not be overridden in write requests.

### Compaction
Time-series data is compacted using TimeWindowCompactionStrategy, a known improved over DateTieredCompactionStrategy. Data is
optimised for queries with a single day. The penalty of reading multiple days is small, a few disk seeks, compared to the
otherwise overhead of reading a significantly larger amount of data.

### Benchmarking
Benchmarking the new datamodel demonstrates a significant performance improvement on reads. How much of this translates to te
Zipkin UI is hard to tell due to the complexity of CassandraSpanConsumer and how searches are possible. Benchmarking stress
profiles are found in traces-stress.yaml and trace_by_service_span-stress.yaml.
