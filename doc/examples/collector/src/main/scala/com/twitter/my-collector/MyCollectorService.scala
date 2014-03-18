package com.twitter.mycollector

import com.twitter.finagle.stats.StatsReceiver
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.cassandra.CassieSpanStoreFactory
import com.twitter.zipkin.collector.{SpanReceiver, ZipkinQueuedCollectorFactory}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.receiver.scribe.ScribeSpanReceiverFactory
import com.twitter.zipkin.storage.WriteSpanStore
import com.twitter.zipkin.zookeeper.ZooKeeperClientFactory

object ZipkinCollectorServer extends TwitterServer
  with ZipkinQueuedCollectorFactory
  with CassieSpanStoreFactory
  with ZooKeeperClientFactory
  with ScribeSpanReceiverFactory
{
  def newReceiver(receive: Seq[Span] => Future[Unit], stats: StatsReceiver): SpanReceiver =
    newScribeSpanReceiver(receive, stats.scope("scribeSpanReceiver"))

  def newSpanStore(stats: StatsReceiver): WriteSpanStore =
    newCassandraStore(stats.scope("cassie"))

  def main() {
    val collector = newCollector(statsReceiver)
    onExit { collector.close() }
    Await.ready(collector)
  }
}
