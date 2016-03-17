# spanstore-elasticsearch

This is an Elasticsearch 2 SpanStore. The SpanStore utilizies the Elasticsearch Java client
library with a node client for optimal performance.

Spans are stored into daily indices, for example spans with a timestamp falling on 2016/03/19
will be stored in an index like zipkin-2016-03-19. There is no support for TTL through this SpanStore.
It is recommended instead to use [Elastic Curator](https://www.elastic.co/guide/en/elasticsearch/client/curator/current/about.html)
to remove indices older than the point you are interested in.

`zipkin.elasticsearch.ElasticsearchConfig` includes defaults that will operate
against a local Elasticsearch installation.

