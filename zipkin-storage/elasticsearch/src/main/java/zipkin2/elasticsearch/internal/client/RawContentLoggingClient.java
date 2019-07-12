package zipkin2.elasticsearch.internal.client;

import com.linecorp.armeria.client.Client;
import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.SimpleDecoratingClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import java.util.concurrent.CompletableFuture;

/**
 * A simple decorator to record raw content strings into HTTP logs. By default, Armeria only logs
 * RPC request / responses, not raw HTTP client content.
 */
public class RawContentLoggingClient extends SimpleDecoratingClient<HttpRequest, HttpResponse> {

  /**
   * Creates a new instance that decorates the specified {@link Client}.
   */
  public RawContentLoggingClient(Client<HttpRequest, HttpResponse> delegate) {
    super(delegate);
  }

  @Override public HttpResponse execute(ClientRequestContext ctx, HttpRequest req) {
    return HttpResponse.from(
      req.aggregateWithPooledObjects(ctx.eventLoop(), ctx.alloc())
        .thenCompose(aggregatedReq -> {
          if (!aggregatedReq.content().isEmpty()) {
            ctx.logBuilder().requestContent(aggregatedReq.contentUtf8(), null);
          }
          try {
            return delegate().execute(ctx, HttpRequest.of(aggregatedReq))
              .aggregateWithPooledObjects(ctx.eventLoop(), ctx.alloc());
          } catch (Exception e) {
            CompletableFuture<AggregatedHttpResponse> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
          }
        })
        .thenApply(aggregatedResp -> {
          if (!aggregatedResp.content().isEmpty()) {
            ctx.logBuilder().responseContent(aggregatedResp.contentUtf8(), null);
          }
          return HttpResponse.of(aggregatedResp);
        }));
  }
}
