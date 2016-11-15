# storage-elasticsearch-http

This is is a plugin to the Elasticsearch storage component, which uses
HTTP by way of [OkHttp 3](https://github.com/square/okttp) and
[Moshi](https://github.com/square/moshi). This currently supports both
2.x and 5.x version families.

See [storage-elasticsearch](../elasticsearch) for more details.

## Customizing the ingest pipeline

When using Elasticsearch 5.x, you can setup an [ingest pipeline](https://www.elastic.co/guide/en/elasticsearch/reference/master/pipeline.html)
to perform custom processing.

Here's an example, which you'd setup prior to configuring Zipkin to use
it via `zipkin.storage.elasticsearch.http.HttpClientBuilder.pipeline`


```
PUT _ingest/pipeline/zipkin
{
  "description" : "add collector_timestamp_millis",
  "processors" : [
    {
      "set" : {
        "field": "collector_timestamp_millis",
        "value": "{{_ingest.timestamp}}"
      }
    }
  ]
}
```
