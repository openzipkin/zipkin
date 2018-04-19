/**
 * Copyright 2015-2018 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.server.internal.brave;

import brave.Span;
import brave.http.HttpServerAdapter;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.propagation.CurrentTraceContext;
import brave.propagation.CurrentTraceContext.Scope;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.ExceptionHandler;
import io.undertow.util.HeaderMap;
import java.net.InetSocketAddress;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin.server.internal.ConditionalOnSelfTracing;
import zipkin2.Endpoint;

/** TODO: when brave gets undertow tracing by default, switch to that */
@ConditionalOnSelfTracing
@Configuration
public class TracingHttpHandlerConfiguration {

  @Bean @Qualifier("httpTracingCustomizer") UndertowDeploymentInfoCustomizer httpTracingCustomizer(
    HttpTracing httpTracing) {
    TracingHttpHandler.Wrapper result = new TracingHttpHandler.Wrapper(httpTracing);
    return info -> info.addInitialHandlerChainWrapper(result);
  }

  static final class TracingHttpHandler implements HttpHandler {
    static final Propagation.Getter<HeaderMap, String>
      GETTER = new Propagation.Getter<HeaderMap, String>() {
      @Override public String get(HeaderMap carrier, String key) {
        return carrier.getFirst(key);
      }

      @Override public String toString() {
        return "HttpServerRequest::getHeader";
      }
    };

    static final class Wrapper implements HandlerWrapper {
      final HttpTracing httpTracing;

      Wrapper(HttpTracing httpTracing) {
        this.httpTracing = httpTracing;
      }

      @Override public HttpHandler wrap(HttpHandler next) {
        return new TracingHttpHandler(httpTracing, next);
      }
    }

    final CurrentTraceContext currentTraceContext;
    final HttpServerHandler<HttpServerExchange, HttpServerExchange> serverHandler;
    final TraceContext.Extractor<HeaderMap> extractor;
    final HttpHandler next;

    TracingHttpHandler(HttpTracing httpTracing, HttpHandler next) {
      this.currentTraceContext = httpTracing.tracing().currentTraceContext();
      this.serverHandler = HttpServerHandler.create(httpTracing, new Adapter());
      this.extractor = httpTracing.tracing().propagation().extractor(GETTER);
      this.next = next;
    }

    @Override public void handleRequest(HttpServerExchange exchange) throws Exception {
      if (!exchange.isComplete()) {
        Span span = serverHandler.handleReceive(extractor, exchange.getRequestHeaders(), exchange);
        exchange.addExchangeCompleteListener((exch, nextListener) -> {
          try {
            nextListener.proceed();
          } finally {
            serverHandler.handleSend(exch, exch.getAttachment(ExceptionHandler.THROWABLE), span);
          }
        });
        try (Scope scope = currentTraceContext.newScope(span.context())) {
          next.handleRequest(exchange);
        } catch (Exception | Error e) { // move the error to where the complete listener can see it
          exchange.putAttachment(ExceptionHandler.THROWABLE, e);
          throw e;
        }
      } else {
        next.handleRequest(exchange);
      }
    }
  }

  static final class Adapter extends HttpServerAdapter<HttpServerExchange, HttpServerExchange> {
    @Override public String method(HttpServerExchange request) {
      return request.getRequestMethod().toString();
    }

    @Override public String path(HttpServerExchange request) {
      return request.getRequestPath();
    }

    @Override public String url(HttpServerExchange request) {
      return request.getRequestURL();
    }

    @Override public String requestHeader(HttpServerExchange request, String name) {
      return request.getRequestHeaders().getFirst(name);
    }

    @Override public Integer statusCode(HttpServerExchange response) {
      return response.getStatusCode();
    }

    @Override
    public boolean parseClientAddress(HttpServerExchange req, Endpoint.Builder builder) {
      if (super.parseClientAddress(req, builder)) return true;
      InetSocketAddress addr = (InetSocketAddress) req.getConnection().getPeerAddress();
      if (builder.parseIp(addr.getAddress())) {
        builder.port(addr.getPort());
        return true;
      }
      return false;
    }
  }
}
