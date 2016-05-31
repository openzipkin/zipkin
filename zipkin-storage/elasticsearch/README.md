# storage-elasticsearch

This Elasticsearch 2 storage component includes a `GuavaSpanStore` and `GuavaSpanConsumer`.
`GuavaSpanStore.getDependencies()` returns pre-aggregated dependency links (ex via [zipkin-dependencies-spark](https://github.com/openzipkin/zipkin-dependencies-spark)).

The implementation uses Elasticsearch Java API's [node client](https://www.elastic.co/guide/en/elasticsearch/guide/master/_talking_to_elasticsearch.html#_java_api) for optimal performance.

Spans are stored into daily indices, for example spans with a timestamp falling on 2016/03/19
will be stored in an index like zipkin-2016-03-19. There is no support for TTL through this SpanStore.
It is recommended instead to use [Elastic Curator](https://www.elastic.co/guide/en/elasticsearch/client/curator/current/about.html)
to remove indices older than the point you are interested in.

`zipkin.storage.elasticsearch.ElasticsearchStorage.Builder` includes defaults
that will operate against a local Elasticsearch installation.

## Testing this component
This module conditionally runs integration tests against a local Elasticsearch instance.

Tests are configured to automatically access Elasticsearch started with its defaults.
To ensure tests execute, download an Elasticsearch 2.x distribution, extract it, and run `bin/elasticsearch`. 

If you run tests via Maven or otherwise when Elasticsearch is not running,
you'll notice tests are silently skipped.
```
Results :

Tests run: 50, Failures: 0, Errors: 0, Skipped: 48
```

This behaviour is intentional: We don't want to burden developers with
installing and running all storage options to test unrelated change.
That said, all integration tests run on pull request via Travis.
