# storage-elasticsearch-http

This is is a plugin to the Elasticsearch storage component, which uses
HTTP by way of [Armeria](https://github.com/line/armeria) and
[Moshi](https://github.com/square/moshi). This uses Elasticsearch 5+
features, but is tested against Elasticsearch 6-7.x.

## Multiple hosts
Most users will supply a DNS name that's mapped to multiple A or AAAA
records. For example, `http://elasticsearch:9200` will use normal host
lookups to get the list of IP addresses, though you can alternatively supply 
a list of http base urls. In either case, all of the resolved IP addresses
from all provided hosts will be iterated over round-robin, with requests made
only to healthy addresses.

Here are some examples:

* http://1.1.1.1:9200,http://2.2.2.2:19200
* http://1.1.1.1:9200,http://[2001:db8::c001]:9200
* http://elasticsearch:9200,http://1.2.3.4:9200
* http://elasticsearch-1:9200,http://elasticsearch-2:9200

## Format
Spans are stored in version 2 format, which is the same as the [v2 POST endpoint](https://zipkin.io/zipkin-api/#/default/post_spans)
with one difference described below. We add a "timestamp_millis" field
to aid in integration with other tools.

### Timestamps
Zipkin's timestamps are in epoch microseconds, which is not a supported date type in Elasticsearch.
In consideration of tools like like Kibana, this component adds "timestamp_millis" when writing
spans. This is mapped to the Elasticsearch date type, so can be used to any date-based queries.

## Indexes
Spans are stored into daily indices, for example spans with a timestamp
falling on 2016/03/19 will be stored in the index named 'zipkin:span-2016-03-19'
or 'zipkin-span-2016-03-19' if using Elasticsearch version 7 or higher.
There is no support for TTL through this SpanStore. It is recommended
instead to use [Elastic Curator](https://www.elastic.co/guide/en/elasticsearch/client/curator/current/about.html)
to remove indices older than the point you are interested in.

### Customizing daily index format
The daily index format can be adjusted in two ways. You can change the
index prefix from 'zipkin' to something else. You can also change
the date separator from '-' to something else.
`ElasticsearchStorage.Builder.index` and `ElasticsearchStorage.Builder.dateSeparator`
control the daily index format.

For example, using Elasticsearch 7+, spans with a timestamp falling on
2016/03/19 end up in the index 'zipkin-span-2016-03-19'. When the date
separator is '.', the index would be 'zipkin-span-2016.03.19'.

### String Mapping
The Zipkin api implies aggregation and exact match (keyword) on string
fields named `traceId` and `name` and `serviceName`. Indexing on these
fields is limited to 256 characters eventhough storage is currently
unbounded.

### Query indexing
To support the zipkin query api, a special index field named `_q` is
added to documents, containing annotation values and tag entry pairs.
Ex: the tag `"error": "500"` results in `"_q":["error", "error=500"]`.
The values in `q` are limited to 256 characters and searched as keywords.

You can check these manually like so:
```bash
$ curl -s 'localhost:9200/zipkin*span-2017-08-11/_search?q=_q:error=500'
```

The reason for special casing is around dotted name constraints. Tags
are stored as a dictionary. Some keys include inconsistent number of dots
(ex "error" and "error.message"). Elasticsearch cannot index these as it
inteprets them as fields, and dots in fields imply an object path.

### Trace Identifiers
Unless `ElasticsearchStorage.Builder.strictTraceId` is set to false,
trace identifiers are unanalyzed keywords (exact string match). This
means that trace IDs should be written fixed length as either 16 or 32
lowercase hex characters, corresponding to 64 or 128 bit length. If
writing a custom collector in a different language, make sure you trace
identifiers the same way.

#### Migrating from 64 to 128-bit trace IDs
When [migrating from 64 to 128-bit trace IDs](../../zipkin-server/README.md#migrating-from-64-to-128-bit-trace-ids),
`ElasticsearchStorage.Builder.strictTraceId` will be false, and traceId
fields will be tokenized to support mixed lookup. This setting should
only be used temporarily, but is explained below.

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
$ curl -s 'localhost:9200/zipkin*span-2017-08-22/_analyze' -d '{
      "text": "48485a3953bb61246b221d5bc9e6496c",
      "analyzer": "traceId_analyzer"
  }'|jq '.tokens|.[]|.token'
  "48485a3953bb61246b221d5bc9e6496c"
  "6b221d5bc9e6496c"
```

### Disabling indexing
Indexing is a good default, but some sites who don't use Zipkin UI's
"Find a Trace" screen may want to disable indexing. This means templates
will opt-out of analyzing any data in `span`, except `traceId`. This
also means the special fields `_q` and `timestamp_millis` will neither
be written, nor analyzed.

[Disabling search](../../README.md#disabling-search) disables indexing.

## Customizing the ingest pipeline

You can setup an [ingest pipeline](https://www.elastic.co/guide/en/elasticsearch/reference/master/pipeline.html) to perform custom processing.

Here's an example, which you'd setup prior to configuring Zipkin to use
it via `ElasticsearchStorage.Builder.pipeline`


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

## Tuning

### Autocomplete indexing
Redundant requests to store autocomplete values are ignored for an hour
to reduce load. This is implemented by
[DelayLimiter](../../zipkin/src/main/java/zipkin2/internal/DelayLimiter.java)
