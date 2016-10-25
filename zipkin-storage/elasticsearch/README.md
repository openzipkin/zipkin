# storage-elasticsearch

This Elasticsearch 2 storage component includes a `GuavaSpanStore` and `GuavaSpanConsumer`.
Until [zipkin-dependencies](https://github.com/openzipkin/zipkin-dependencies) is run, `ElasticsearchSpanStore.getDependencies()` will return empty.

The implementation uses Elasticsearch Java API's [transport client](https://www.elastic.co/guide/en/elasticsearch/guide/master/_talking_to_elasticsearch.html#_java_api) for optimal performance.

`zipkin.storage.elasticsearch.ElasticsearchStorage.Builder` includes defaults
that will operate against a local Elasticsearch installation.

## Indexes
Spans are stored into daily indices, for example spans with a timestamp falling on 2016/03/19
will be stored in an index like zipkin-2016-03-19. There is no support for TTL through this SpanStore.
It is recommended instead to use [Elastic Curator](https://www.elastic.co/guide/en/elasticsearch/client/curator/current/about.html)
to remove indices older than the point you are interested in.

### Timestamps
Zipkin's timestamps are in epoch microseconds, which is not a supported date type in Elasticsearch.
In consideration of tools like like Kibana, this component adds "timestamp_millis" when writing
spans. This is mapped to the Elasticsearch date type, so can be used to any date-based queries.

### Trace Identifiers
The index template tokenizes trace identifiers to match on either 64-bit
or 128-bit length. This allows span lookup by 64-bit trace ID to include
spans reported with 128-bit variants of the same id. This allows interop
with tools who only support 64-bit ids, and buys time for applications
to upgrade to 128-bit instrumentation.

For example, application A starts a trace with a 128-bit `traceId`
"48485a3953bb61246b221d5bc9e6496c". The next hop, application B, doesn't
yet support 128-bit ids, B truncates `traceId` to "6b221d5bc9e6496c".
When `SpanStore.getTrace(toLong("6b221d5bc9e6496c"))` executes, it
is able to retrieve spans with the longer `traceId`, due to tokenization
setup in the index template.

To see this in action, you can run a test command like so against one of
your indexes:

```bash
# the output below shows which tokens will match on the trace id supplied.
$ curl -s localhost:9200/test_zipkin_http-2016-10-26/_analyze -d '{
      "text": "48485a3953bb61246b221d5bc9e6496c",
      "analyzer": "traceId_analyzer"
  }'|jq '.tokens|.[]|.token'
  "48485a3953bb61246b221d5bc9e6496c"
  "6b221d5bc9e6496c"
```

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
