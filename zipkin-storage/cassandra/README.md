# storage-cassandra

This CQL-based Cassandra 2.2+ storage component includes a `GuavaSpanStore` and `GuavaSpanConsumer`.
`GuavaSpanStore.getDependencies()` returns pre-aggregated dependency links (ex via [zipkin-dependencies-spark](https://github.com/openzipkin/zipkin-dependencies-spark)).

The implementation uses the [Datastax Java Driver 3.x](https://github.com/datastax/java-driver).

The CQL schema is the same as [zipkin-scala](https://github.com/openzipkin/zipkin/tree/master/zipkin-cassandra).

`zipkin.storage.cassandra.CassandraStorage.Builder` includes defaults that will
operate against a local Cassandra installation.

