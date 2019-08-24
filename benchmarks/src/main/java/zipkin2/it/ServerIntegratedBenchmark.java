package zipkin2.it;

import brave.Tracing;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.HttpClientBuilder;
import com.linecorp.armeria.client.brave.BraveClient;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.brave.RequestContextCurrentTraceContext;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.brave.BraveService;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import zipkin.server.ZipkinServer;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Sender;
import zipkin2.reporter.okhttp3.OkHttpSender;

public class ServerIntegratedBenchmark {

  public static void main(String[] args) throws Exception {
    Tracing backendTracing = TracingFactory.create("backend");

    Server backend = new ServerBuilder()
      .http(9000)
      .serviceUnder("/", ((ctx, req) -> HttpResponse.of(HttpStatus.OK)))
      .decorator(BraveService.newDecorator(backendTracing))
      .build();

    Tracing frontendTracing = TracingFactory.create("frontend");

    HttpClient backendClient =
      new HttpClientBuilder("http://localhost:9000/")
        .decorator(BraveClient.newDecorator(frontendTracing, "backend"))
        .build();

    Server frontend = new ServerBuilder()
      .http(8081)
      .serviceUnder("/", ((ctx, req) -> backendClient.get("/")))
      .decorator(BraveService.newDecorator(frontendTracing))
      .build();

    try {
      backend.start().join();
      frontend.start().join();
      ZipkinServer.main(new String[0]);

      HttpClient frontendClient = HttpClient.of("http://localhost:8081/");

      long startTimeNanos = System.nanoTime();
      long endTimeNanos = startTimeNanos + TimeUnit.SECONDS.toNanos(100);

      while (System.nanoTime() < endTimeNanos) {
        frontendClient.get("/").aggregate().join();
      }

      Thread.sleep(Long.MAX_VALUE);
    } finally {
      backend.stop().join();
      frontend.stop().join();
    }
  }

  static class TracingFactory {

    /** Controls aspects of tracing such as the name that shows up in the UI */
    static Tracing create(String serviceName) {
      return Tracing.newBuilder()
        .localServiceName(serviceName)
        .currentTraceContext(RequestContextCurrentTraceContext.ofDefault())
        .spanReporter(spanReporter(sender()))
        .build();
    }

    /** Configuration for how to send spans to Zipkin */
    static Sender sender() {
      return OkHttpSender.create("http://localhost:9411/api/v2/spans");
    }

    /** Configuration for how to buffer spans into messages for Zipkin */
    static AsyncReporter<Span> spanReporter(Sender sender) {
      final AsyncReporter<Span> spanReporter = AsyncReporter.create(sender);

      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        spanReporter.close(); // Make sure spans are reported on shutdown
        try {
          sender.close(); // Release any network resources used to send spans
        } catch (IOException e) {
          Logger.getAnonymousLogger().warning("error closing trace sender: " + e.getMessage());
        }
      }));

      return spanReporter;
    }
  }
}
