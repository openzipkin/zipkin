# storage-cassandra

This CQL-based Cassandra 2.1+ storage component includes a `GuavaSpanStore` and `GuavaSpanConsumer`.
`GuavaSpanStore.getDependencies()` returns pre-aggregated dependency links (ex via [zipkin-dependencies-spark](https://github.com/openzipkin/zipkin-dependencies-spark)).

The implementation uses [zipkin-cassandra](https://github.com/openzipkin/zipkin/tree/master/zipkin-cassandra-core),
which in turn uses [Datastax Java Driver 2.x](https://github.com/datastax/java-driver).

The CQL schema is the same as [zipkin-scala](https://github.com/openzipkin/zipkin/tree/master/zipkin-cassandra).

`zipkin.cassandra.CassandraConfig` includes defaults that will operate
against a local Cassandra installation.

