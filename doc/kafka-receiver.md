## Kafka Receiver

To change Zipkin's transport mechanism to use kafka.
 * change the contents of the Main.scala file to below code
 * this config requires cassandra and zookeeper
 * remove '-f' from zipkin-receiver-kafka/build.gradle (i.e. args '-f', "${projectDir}...")
 * ```bin/collector cassandra```


```scala
package com.twitter.zipkin.collector

import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.cassandra.CassandraSpanStoreFactory
import com.twitter.zipkin.collector.{SpanReceiver, ZipkinQueuedCollectorFactory}
import com.twitter.zipkin.common._
import com.twitter.zipkin.receiver.kafka.KafkaSpanReceiverFactory
import com.twitter.zipkin.storage.cassandra.CassandraSpanStore
import kafka.serializer.Decoder

import com.twitter.zipkin.receiver.kafka.SpanDecoder

// 'Main' object referenced by project's build.gradle
object Main extends TwitterServer
  with ZipkinQueuedCollectorFactory
  with CassandraSpanStoreFactory
  with KafkaSpanReceiverFactory
{
  def newReceiver(receive: Seq[ThriftSpan] => Future[Unit], stats: StatsReceiver): SpanReceiver = {
    newKafkaSpanReceiver(receive, stats.scope("kafkaSpanReceiver"), Some(new SpanDecoder()), new SpanDecoder())
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
