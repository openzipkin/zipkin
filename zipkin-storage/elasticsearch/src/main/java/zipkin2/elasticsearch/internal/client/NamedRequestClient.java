package zipkin2.elasticsearch.internal.client;

import com.linecorp.armeria.client.Client;
import com.linecorp.armeria.client.ClientRequestContext;
import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.SimpleDecoratingClient;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.RpcRequest;
import com.linecorp.armeria.common.util.SafeCloseable;
import io.netty.util.AttributeKey;
import java.util.function.Function;

/** A decorator that will set the method of a logged request to a custom name if desired. */
public class NamedRequestClient extends SimpleDecoratingClient<HttpRequest, HttpResponse> {

  static final AttributeKey<String> CUSTOM_METHOD_NAME =
    AttributeKey.valueOf(NamedRequestClient.class, "CUSTOM_METHOD_NAME");

  public static Function<Client<HttpRequest, HttpResponse>, Client<HttpRequest, HttpResponse>>
  newDecorator() {
    return NamedRequestClient::new;
  }

  public static SafeCloseable withCustomMethodName(String name) {
    return Clients.withContextCustomizer(ctx -> ctx.attr(CUSTOM_METHOD_NAME).set(name));
  }

  NamedRequestClient(Client<HttpRequest, HttpResponse> delegate) {
    super(delegate);
  }

  @Override public HttpResponse execute(ClientRequestContext ctx, HttpRequest req)
    throws Exception {
    String customMethodName = ctx.attr(CUSTOM_METHOD_NAME).get();
    if (customMethodName != null) {
      ctx.logBuilder().requestContent(RpcRequest.of(HttpClient.class, customMethodName), req);
    }
    return delegate().execute(ctx, req);
  }
}
