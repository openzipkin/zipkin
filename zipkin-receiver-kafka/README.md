## Kafka Receiver

To change Zipkin's transport mechanism to use kafka.
 * drop code below into `zipkin-collector-service/src/main/scala/com/twitter/zipkin/collector/Main.scala`
 * this config requires cassandra
 * remove '-f' from zipkin-collector-service/build.gradle (i.e. args '-f', "${projectDir}...")
 * ```bin/collector cassandra```


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

// 'Main' object referenced by project's build.gradle
object Main extends TwitterServer
with ZipkinQueuedCollectorFactory
with CassandraSpanStoreFactory
with KafkaSpanReceiverFactory
{
  // set the admin port to the same as the existing collector
  override val defaultHttpPort = 9900

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
