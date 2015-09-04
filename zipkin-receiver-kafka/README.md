## Kafka Receiver
TODO: write me and include the flow and OSS instrumentation that logs spans to kafka.

### Enabling the Kafka receiver with zipkin-collector-service
`zipkin-collector-service` typically receives spans from scribe. To enable kafka, you need to
assign the `KAFKA_ZOOKEEPER` variable when starting the process.

For example, if you are starting from a zipkin's source tree, you might assign this way:
```bash
$ KAFKA_ZOOKEEPER=127.0.0.1:2181 bin/collector
# or if zipkin isn't the right topic name
$ KAFKA_ZOOKEEPER=127.0.0.1:2181 KAFKA_TOPIC=notzipkin bin/collector
```

### Creating a custom Kafka Receiver process

You can also create a custom receiver and start that. Here's an example main class that directs
spans from Kakfa into Cassandra:

```scala
package com.twitter.zipkin.collector

import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.cassandra.CassandraSpanStoreFactory
import com.twitter.zipkin.receiver.kafka.KafkaSpanReceiverFactory
import com.twitter.zipkin.storage.cassandra.CassandraSpanStore

import com.twitter.zipkin.receiver.kafka.SpanDecoder

object Main extends TwitterServer
 with ZipkinQueuedCollectorFactory
 with CassandraSpanStoreFactory
 with KafkaSpanReceiverFactory
{
  // you may want the admin port to be the same as the default collector
  override val defaultHttpPort = 9900

  def newReceiver(process: Seq[ThriftSpan] => Future[Unit], stats: StatsReceiver): SpanReceiver = {
    newKafkaSpanReceiver(receive, stats.scope("kafkaSpanReceiver"))
  }

  def newSpanStore(stats: StatsReceiver): CassandraSpanStore =
    newCassandraStore(stats.scope("cassandra"))

  def main() {
    val collector = newCollector(statsReceiver)
    onExit { collector.close() }
    Await.ready(collector)
  }
}
```
