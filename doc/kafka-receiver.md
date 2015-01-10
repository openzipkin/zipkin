## Kafka Receiver

To change Zipkin's transport mechanism to use kafka.
 * change the contents of the Main.scala file
 * this setup requires cassandra
 * ```bin/collector cassandra```


```scala
# zipkin/zipkin-collector-service/src/main/scala/com/twitter/zipkin/collector/Main.scala

import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.cassandra.CassieSpanStoreFactory
import com.twitter.zipkin.collector.{SpanReceiver, ZipkinQueuedCollectorFactory}
import com.twitter.zipkin.common._
import com.twitter.zipkin.receiver.kafka.KafkaSpanReceiverFactory
import com.twitter.zipkin.storage.WriteSpanStore
import com.twitter.zipkin.zookeeper.ZooKeeperClientFactory
import kafka.serializer.Decoder

import com.twitter.zipkin.receiver.kafka.SpanDecoder

object ZipkinKafkaCollectorServer extends TwitterServer
  with ZipkinQueuedCollectorFactory
  with CassieSpanStoreFactory
  with ZooKeeperClientFactory
  with KafkaSpanReceiverFactory
{
  def newReceiver(receive: Seq[ThriftSpan] => Future[Unit], stats: StatsReceiver): SpanReceiver = {
    newKafkaSpanReceiver(receive, stats.scope("kafkaSpanReceiver"), Some(new SpanDecoder()), new SpanDecoder())
  }

  def newSpanStore(stats: StatsReceiver): WriteSpanStore =
    newCassandraStore(stats.scope("cassie"))

  def main() {
    val collector = newCollector(statsReceiver)
    onExit { collector.close() }
    Await.ready(collector)
  }
}
```