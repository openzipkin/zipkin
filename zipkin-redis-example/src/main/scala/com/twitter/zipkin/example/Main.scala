package com.twitter.zipkin.example

import com.twitter.finagle.Httpx
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Closable}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.query.ThriftQueryService
import com.twitter.zipkin.query.constants.DefaultAdjusters
import com.twitter.zipkin.receiver.scribe.ScribeSpanReceiverFactory
import com.twitter.zipkin.redis.RedisSpanStoreFactory
import com.twitter.zipkin.web.ZipkinWebFactory
import com.twitter.zipkin.{thriftscala => thrift}

object Main extends TwitterServer
  with ScribeSpanReceiverFactory
  with ZipkinWebFactory
  with RedisSpanStoreFactory
{
  def main() {
    val store = newRedisSpanStore()

    val convert: Seq[thrift.Span] => Seq[Span] = { _.map(_.toSpan) }
    val receiver = newScribeSpanReceiver(convert andThen store, statsReceiver.scope("scribeSpanReceiver"))
    val query = new ThriftQueryService(store, adjusters = DefaultAdjusters)
    val webService = newWebServer(query, statsReceiver.scope("web"))
    val web = Httpx.serve(webServerPort(), webService)

    val closer = Closable.sequence(web, receiver, store)
    closeOnExit(closer)

    println("running and ready")
    Await.all(web, receiver, store)
  }
}
