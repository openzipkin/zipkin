/**
 * Copyright 2015-2017 The OpenZipkin Authors
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

import io.prometheus.client.Histogram;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.spring.boot.EnablePrometheusEndpoint;
import io.prometheus.client.spring.boot.EnableSpringBootMetricsCollector;
import io.prometheus.client.spring.web.EnablePrometheusTiming;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import org.springframework.boot.context.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@EnablePrometheusEndpoint
@EnableSpringBootMetricsCollector
@EnablePrometheusTiming
@Configuration
public class ZipkinPrometheusMetricsAutoConfiguration {
  // needs to be a JVM singleton
  static final Histogram http_request_duration_seconds = Histogram.build()
    .labelNames("path", "method")
    .help("Response time histogram")
    .name("http_request_duration_seconds")
    .register();

  ZipkinPrometheusMetricsAutoConfiguration() {
    DefaultExports.initialize();
  }

  @Bean Histogram httpRequestDuration() {
    return http_request_duration_seconds;
  }

  @Bean UndertowDeploymentInfoCustomizer httpRequestDurationCustomizer() {
    HttpRequestDurationHandler.Wrapper result =
      new HttpRequestDurationHandler.Wrapper(http_request_duration_seconds);
    return info -> info.addInitialHandlerChainWrapper(result);
  }

  static final class HttpRequestDurationHandler implements HttpHandler {
    static final class Wrapper implements HandlerWrapper {
      final Histogram httpRequestDuration;

      Wrapper(Histogram httpRequestDuration) {
        this.httpRequestDuration = httpRequestDuration;
      }

      @Override public HttpHandler wrap(HttpHandler next) {
        return new HttpRequestDurationHandler(httpRequestDuration, next);
      }
    }

    final Histogram httpRequestDuration;
    final HttpHandler next;

    HttpRequestDurationHandler(Histogram httpRequestDuration, HttpHandler next) {
      this.httpRequestDuration = httpRequestDuration;
      this.next = next;
    }

    @Override public void handleRequest(HttpServerExchange exchange) throws Exception {
      if (!exchange.isComplete()) {
        Histogram.Timer timer = httpRequestDuration
          .labels(exchange.getRelativePath(), exchange.getRequestMethod().toString())
          .startTimer();
        exchange.addExchangeCompleteListener((exchange1, nextListener) -> {
          timer.observeDuration();
          nextListener.proceed();
        });
      }
      next.handleRequest(exchange);
    }
  }
}
