# storage-elasticsearch

This Elasticsearch 2 storage component includes a `GuavaSpanStore` and `GuavaSpanConsumer`.
`GuavaSpanStore.getDependencies()` returns pre-aggregated dependency links (ex via [zipkin-dependencies-spark](https://github.com/openzipkin/zipkin-dependencies-spark)).

The implementation uses Elasticsearch Java API's [node client](https://www.elastic.co/guide/en/elasticsearch/guide/master/_talking_to_elasticsearch.html#_java_api) for optimal performance.

Spans are stored into daily indices, for example spans with a timestamp falling on 2016/03/19
will be stored in an index like zipkin-2016-03-19. There is no support for TTL through this SpanStore.
It is recommended instead to use [Elastic Curator](https://www.elastic.co/guide/en/elasticsearch/client/curator/current/about.html)
to remove indices older than the point you are interested in.

`zipkin.elasticsearch.ElasticsearchConfig` includes defaults that will operate
against a local Elasticsearch installation.

