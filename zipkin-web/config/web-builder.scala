import com.twitter.finagle.zipkin.thrift.ZipkinTracer
import com.twitter.zipkin.builder.{QueryClient, WebBuilder}
import java.net.InetSocketAddress

val queryClient = QueryClient.static(new InetSocketAddress("localhost", 3002)) map {
  _.tracerFactory(ZipkinTracer())
}
WebBuilder("http://localhost:8080/", queryClient)
