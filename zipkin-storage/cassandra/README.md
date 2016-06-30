# storage-cassandra

This CQL-based Cassandra 2.2+ storage component includes a `GuavaSpanStore` and `GuavaSpanConsumer`.
`GuavaSpanStore.getDependencies()` returns pre-aggregated dependency links (ex via [zipkin-dependencies-spark](https://github.com/openzipkin/zipkin-dependencies-spark)).

The implementation uses the [Datastax Java Driver 3.x](https://github.com/datastax/java-driver).

The CQL schema is the same as [zipkin-scala](https://github.com/openzipkin/zipkin/tree/master/zipkin-cassandra).

`zipkin.storage.cassandra.CassandraStorage.Builder` includes defaults that will
operate against a local Cassandra installation.

## Logging
Queries are logged to the category "com.datastax.driver.core.QueryLogger" when debug or trace is
enabled via SLF4J. Trace level includes bound values.

See [Logging Query Latencies](http://docs.datastax.com/en/developer/java-driver/2.1/supplemental/manual/logging/#logging-query-latencies) for more details.

## Testing this component
This module conditionally runs integration tests against a local Cassandra instance.

Tests are configured to automatically access Cassandra started with its defaults.
To ensure tests execute, download a Cassandra 2.2-3.4 distribution, extract it, and run `bin/cassandra`. 

If you run tests via Maven or otherwise when Cassandra is not running,
you'll notice tests are silently skipped.
```
Results :

Tests run: 62, Failures: 0, Errors: 0, Skipped: 48
```

This behaviour is intentional: We don't want to burden developers with
installing and running all storage options to test unrelated change.
That said, all integration tests run on pull request via Travis.
