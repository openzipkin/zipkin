package com.twitter.mycollector

import com.twitter.finagle.stats.StatsReceiver
import com.twitter.util.Future
import com.twitter.zipkin.cassandra.CassieSpanStoreFactory
import com.twitter.zipkin.collector.{SpanReceiver, ZipkinQueuedCollectorServer}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.receiver.scribe.ScribeSpanReceiverFactory
import com.twitter.zipkin.storage.WriteSpanStore

object MyCollectorService
  extends ZipkinQueuedCollectorServer
  with ScribeSpanReceiverFactory
  with CassieSpanStoreFactory
{
  def newReceiver(stats: StatsReceiver, receive: Seq[Span] => Future[Unit]): SpanReceiver =
    newScribeSpanReceiver(receive, stats.scope("ScribeSpanReceiver"))

  def newSpanStore(stats: StatsReceiver): WriteSpanStore =
    newCassandraStore(stats.scope("CassieSpanStore"))
}
