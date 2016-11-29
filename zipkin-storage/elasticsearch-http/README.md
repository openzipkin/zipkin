# storage-elasticsearch-http

This is is a plugin to the Elasticsearch storage component, which uses
HTTP by way of [OkHttp 3](https://github.com/square/okttp) and
[Moshi](https://github.com/square/moshi). This currently supports both
2.x and 5.x version families.

See [storage-elasticsearch](../elasticsearch) for more details.

## Multiple hosts
Most users will supply a DNS name that's mapped to multiple A or AAAA
records. For example, `http://elasticsearch:9200` will use normal host
lookups to get the list of IP addresses.

You can alternatively supply a list of http base urls. This list is used
to recover from failures. Note that all ports must be the same, and the
scheme must be http, not https.

Here are some examples:

* http://1.1.1.1:9200,http://2.2.2.2:9200
* http://1.1.1.1:9200,http://[2001:db8::c001]:9200
* http://elasticsearch:9200,http://1.2.3.4:9200
* http://elasticsearch-1:9200,http://elasticsearch-2:9200

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
