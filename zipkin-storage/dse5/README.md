# storage-dse5

*This module is experimental. Please help test this, but do not use it in production.*

This CQL-based DSE 5.0+ storage component includes a `GuavaSpanStore` and `GuavaSpanConsumer`.
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
To ensure tests execute, download a DSE 5.0+ distribution, extract it, and run `dse start`.

If you run tests via Maven or otherwise when Cassandra is not running,
you'll notice tests are silently skipped.
```
Results :

Tests run: 62, Failures: 0, Errors: 0, Skipped: 48
```

This behaviour is intentional: We don't want to burden developers with
installing and running all storage options to test unrelated change.
That said, all integration tests run on pull request via Travis.

### Trace indexing

Indexing in CQL is simplified by using both MATERIALIZED VIEWs and DSE Solr Cores.

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
