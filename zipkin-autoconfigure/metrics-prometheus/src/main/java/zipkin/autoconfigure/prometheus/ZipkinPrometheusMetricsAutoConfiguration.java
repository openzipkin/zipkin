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

package zipkin.autoconfigure.prometheus;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration class ZipkinPrometheusMetricsAutoConfiguration {
  // from io.micrometer.spring.web.servlet.WebMvcTags
  private static final Tag URI_NOT_FOUND = Tag.of("uri", "NOT_FOUND");
  private static final Tag URI_REDIRECTION = Tag.of("uri", "REDIRECTION");
  private static final Tag URI_TRACE_V1 = Tag.of("uri", "/api/v1/trace/{traceId}");
  private static final Tag URI_TRACE_V2 = Tag.of("uri", "/api/v2/trace/{traceId}");
  // single-page app requests are forwarded to index: ZipkinUiAutoConfiguration.forwardUiEndpoints
  private static final Tag URI_CROSSROADS = Tag.of("uri", "/zipkin/index.html");

  final PrometheusMeterRegistry registry;
  // https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#production-ready-metrics-spring-mvc
  final String metricName;

  ZipkinPrometheusMetricsAutoConfiguration(
    PrometheusMeterRegistry registry,
    @Value("${management.metrics.web.server.requests-metric-name:http.server.requests}")
      String metricName
  ) {
    this.registry = registry;
    this.metricName = metricName;
  }

  @Bean @Qualifier("httpRequestDurationCustomizer")
  UndertowDeploymentInfoCustomizer httpRequestDurationCustomizer() {
    HttpRequestDurationHandler.Wrapper result =
      new HttpRequestDurationHandler.Wrapper(registry, metricName);
    return info -> info.addInitialHandlerChainWrapper(result);
  }

  static final class HttpRequestDurationHandler implements HttpHandler {
    final MeterRegistry registry;
    final String metricName;
    final HttpHandler next;
    final Clock clock;

    HttpRequestDurationHandler(MeterRegistry registry, String metricName, HttpHandler next) {
      this.registry = registry;
      this.metricName = metricName;
      this.next = next;
      this.clock = registry.config().clock();
    }

    @Override public void handleRequest(HttpServerExchange exchange) throws Exception {
      final long startTime = clock.monotonicTime();
      if (!exchange.isComplete()) {
        exchange.addExchangeCompleteListener((exchange1, nextListener) -> {
          getTimeBuilder(exchange).register(registry)
            .record(clock.monotonicTime() - startTime, TimeUnit.NANOSECONDS);
          nextListener.proceed();
        });
      }
      next.handleRequest(exchange);
    }

    private Timer.Builder getTimeBuilder(HttpServerExchange exchange) {
      return Timer.builder(metricName)
        .tags(this.getTags(exchange))
        .description("Response time histogram")
        .publishPercentileHistogram();
    }

    private Iterable<Tag> getTags(HttpServerExchange exchange) {
      return Arrays.asList(Tag.of("method", exchange.getRequestMethod().toString())
        , uri(exchange)
        , Tag.of("status", Integer.toString(exchange.getStatusCode()))
      );
    }

    /** Ensure metrics cardinality doesn't blow up on variables */
    // TODO: this should really live in the zipkin-server codebase!
    private static Tag uri(HttpServerExchange exchange) {
      int status = exchange.getStatusCode();
      if (status > 299 && status < 400) return URI_REDIRECTION;
      if (status == 404) return URI_NOT_FOUND;

      String uri = getPathInfo(exchange);
      if (uri.startsWith("/zipkin")) {
        if (uri.equals("/zipkin/") || uri.equals("/zipkin")
          || uri.startsWith("/zipkin/traces/")
          || uri.equals("/zipkin/dependency")
          || uri.equals("/zipkin/traceViewer")) {
          return URI_CROSSROADS; // single-page app route
        }

        // un-map UI's api route
        if (uri.startsWith("/zipkin/api")) {
          uri = uri.replaceFirst("/zipkin", "");
        }
      }
      // handle templated routes instead of exploding on trace ID cardinality
      if (uri.startsWith("/api/v1/trace/")) return URI_TRACE_V1;
      if (uri.startsWith("/api/v2/trace/")) return URI_TRACE_V2;
      return Tag.of("uri", uri);
    }

    // from io.micrometer.spring.web.servlet.WebMvcTags
    private static String getPathInfo(HttpServerExchange exchange) {
      String uri = exchange.getRelativePath();
      if (!StringUtils.hasText(uri)) return "/";
      return uri.replaceAll("//+", "/")
        .replaceAll("/$", "");
    }

    static final class Wrapper implements HandlerWrapper {
      final MeterRegistry registry;
      final String metricName;

      Wrapper(MeterRegistry registry, String metricName) {
        this.registry = registry;
        this.metricName = metricName;
      }

      @Override public HttpHandler wrap(HttpHandler next) {
        return new HttpRequestDurationHandler(registry, metricName, next);
      }
    }
  }
}
