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
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.embedded.undertow.UndertowDeploymentInfoCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ZipkinPrometheusMetricsAutoConfiguration {
  private MeterRegistry registry;

  ZipkinPrometheusMetricsAutoConfiguration(MeterRegistry registry) {
    this.registry = registry;
  }

  @Bean @Qualifier("httpRequestDurationCustomizer")
  UndertowDeploymentInfoCustomizer httpRequestDurationCustomizer() {
    HttpRequestDurationHandler.Wrapper result =
      new HttpRequestDurationHandler.Wrapper(registry);
    return info -> info.addInitialHandlerChainWrapper(result);
  }

  static final class HttpRequestDurationHandler implements HttpHandler {
    final MeterRegistry registry;
    final HttpHandler next;
    final Clock clock;

    HttpRequestDurationHandler(MeterRegistry registry, HttpHandler next) {
      this.registry = registry;
      this.next = next;
      this.clock = registry.config().clock();
    }

    @Override public void handleRequest(HttpServerExchange exchange) throws Exception {
      final long startTime = clock.monotonicTime();
      if (!exchange.isComplete()) {
        Timer timer = getTimeBuilder(exchange).register(registry);
        exchange.addExchangeCompleteListener((exchange1, nextListener) -> {
          timer.record(clock.monotonicTime() - startTime, TimeUnit.NANOSECONDS);
          nextListener.proceed();
        });
      }
      next.handleRequest(exchange);
    }

    private Timer.Builder getTimeBuilder(HttpServerExchange exchange) {
      return Timer.builder("http_request_duration")
        .tags(this.getTags(exchange))
        .description("Response time histogram")
        .publishPercentileHistogram();
    }

    private Iterable<Tag> getTags(HttpServerExchange exchange) {
      return Arrays.asList(Tag.of("method", exchange.getRequestMethod().toString())
        , Tag.of("path", exchange.getRelativePath())
        , Tag.of("status", Integer.toString(exchange.getStatusCode()))
      );
    }

    static final class Wrapper implements HandlerWrapper {
      final MeterRegistry registry;

      Wrapper(MeterRegistry registry) {
        this.registry = registry;
      }

      @Override public HttpHandler wrap(HttpHandler next) {
        return new HttpRequestDurationHandler(registry, next);
      }
    }
  }
}
