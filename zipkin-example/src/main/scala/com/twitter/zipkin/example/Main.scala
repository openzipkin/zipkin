package com.twitter.mycollector

import com.twitter.zipkin.conversions.thrift._
import com.twitter.finagle.Http
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.server.{Closer, TwitterServer}
import com.twitter.util.{Await, Closable, Future}
import com.twitter.zipkin.anormdb.AnormDBSpanStoreFactory
import com.twitter.zipkin.collector.SpanReceiver
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.{gen => thrift}
import com.twitter.zipkin.receiver.scribe.ScribeSpanReceiverFactory
import com.twitter.zipkin.zookeeper.ZooKeeperClientFactory
import com.twitter.zipkin.web.ZipkinWebFactory
import com.twitter.zipkin.query.ThriftQueryService
import com.twitter.zipkin.query.constants.DefaultAdjusters
import com.twitter.zipkin.tracegen.ZipkinSpanGenerator

object Main extends TwitterServer with Closer
with ZooKeeperClientFactory
with ScribeSpanReceiverFactory
with ZipkinWebFactory
with AnormDBSpanStoreFactory
with ZipkinSpanGenerator
{
  val genSampleTraces = flag("genSampleTraces", false, "Generate sample traces")

  def main() {
    val store = newAnormSpanStore()
    if (genSampleTraces())
      Await.result(generateTraces(store))

    val convert: Seq[thrift.Span] => Seq[Span] = { _.map(_.toSpan) }
    val receiver = newScribeSpanReceiver(convert andThen store, statsReceiver.scope("scribeSpanReceiver"))
    val query = new ThriftQueryService(store, adjusters = DefaultAdjusters)
    val webService = newWebServer(query, statsReceiver.scope("web"))
    val web = Http.serve(webServerPort(), webService)

    val closer = Closable.sequence(web, receiver, store)
    closeOnExit(closer)

    println("running and ready")
    Await.all(web, receiver, store)
  }
}