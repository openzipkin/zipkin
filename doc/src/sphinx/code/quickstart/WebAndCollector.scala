//#imports
import com.twitter.zipkin.conversions.thrift._
import com.twitter.finagle.Http
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import com.twitter.zipkin.cassandra.CassandraSpanStoreFactory
import com.twitter.zipkin.collector.SpanReceiver
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.{thriftscala => thrift}
import com.twitter.zipkin.receiver.scribe.ScribeSpanReceiverFactory
import com.twitter.zipkin.web.ZipkinWebFactory
import com.twitter.zipkin.query.ThriftQueryService
import com.twitter.zipkin.query.constants.DefaultAdjusters
//#imports

//#web_main
object WebMain extends TwitterServer
  with ZipkinWebFactory
  with CassandraSpanStoreFactory
{
  override def newQueryClient(): ZipkinQuery.FutureIface =
    new ThriftQueryService(newCassandraSpanStore(), adjusters = DefaultAdjusters)

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
  with CassandraSpanStoreFactory
  with ScribeSpanReceiverFactory
{
  def main() {
    val store = newCassandraSpanStore()
    val convert: Seq[thrift.Span] => Seq[Span] = { _.map(_.toSpan) }
    val receiver = newScribeSpanReceiver(convert andThen store)
    closeOnExit(receiver)
    Await.ready(receiver)
  }
}
//#collector_main
