//#imports
import com.twitter.zipkin.conversions.thrift._
import com.twitter.finagle.Http
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import com.twitter.zipkin.cassandra.CassieSpanStoreFactory
import com.twitter.zipkin.collector.SpanReceiver
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.{thriftscala => thrift}
import com.twitter.zipkin.receiver.scribe.ScribeSpanReceiverFactory
import com.twitter.zipkin.zookeeper.ZooKeeperClientFactory
import com.twitter.zipkin.web.ZipkinWebFactory
import com.twitter.zipkin.query.ThriftQueryService
import com.twitter.zipkin.query.constants.DefaultAdjusters
//#imports

//#web_main
object WebMain extends TwitterServer
  with ZipkinWebFactory
  with CassieSpanStoreFactory
{
  override def newQueryClient(): ZipkinQuery.FutureIface =
    new ThriftQueryService(newCassieSpanStore(), adjusters = DefaultAdjusters)

  def main() {
    val web = Http.serve(webServerPort(), newWebServer())
    closeOnExit(web)
    Await.ready(web)
  }
}
//#web_main

//#collector_main
object CollectorMain extends TwitterServer
  with ZipkinCollectorFactory
  with CassieSpanStoreFactory
  with ZooKeeperClientFactory
  with ScribeSpanReceiverFactory
{
  def main() {
    val store = newCassieSpanStore()
    val convert: Seq[thrift.Span] => Seq[Span] = { _.map(_.toSpan) }
    val receiver = newScribeSpanReceiver(convert andThen store)
    closeOnExit(receiver)
    Await.ready(receiver)
  }
}
//#collector_main
