## Kafka Receiver
This receiver polls a Kafka topic for messages that contain TBinaryProtocol big-endian encoded lists of spans.

## Service Configuration

Kafka is an alternate receiver to the [zipkin-collector-service](https://github.com/openzipkin/zipkin/blob/master/zipkin-collector-service/README.md)

It is enabled when the `KAFKA_ZOOKEEPER` environment variable is set. Here are the available options:

   * `KAFKA_ZOOKEEPER`: ZooKeeper host string, comma-separated host:port value. no default.
   * `KAFKA_TOPIC`: Defaults to zipkin
   * `KAFKA_GROUP_ID`: Consumer group this process is consuming on behalf of. Defaults to zipkin
   * `KAFKA_STREAMS`: Count of consumer threads consuming the topic. defaults to 1.

Example usage:

```bash
$ KAFKA_ZOOKEEPER=127.0.0.1:2181 bin/collector
# or if zipkin isn't the right topic name
$ KAFKA_ZOOKEEPER=127.0.0.1:2181 KAFKA_TOPIC=notzipkin bin/collector
```

### Encoding spans into Kafka messages

The message's binary data includes a list header followed by N spans serialized in TBinaryProtocol

```
write_byte(12) // type of the list elements: 12 == struct
write_i32(count) // count of spans that will follow
for (int i = 0; i < count; i++) {
  writeTBinaryProtocol(spans(i))
}
```

If using [zipkin-java](https://github.com/openzipkin/zipkin-java), `Codec.THRIFT.writeSpans(spans)`
implements the above.

#### Legacy encoding
Versions before 1.35 accepted a single span per message, as opposed to a list per message. This
practice is deprecated, but still supported.

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
