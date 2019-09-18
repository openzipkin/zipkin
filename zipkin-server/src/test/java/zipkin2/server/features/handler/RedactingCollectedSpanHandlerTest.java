/*
 * Copyright 2013-2019 The OpenZipkin Authors
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
package zipkin2.server.features.handler;

import com.linecorp.armeria.spring.actuate.ArmeriaSpringActuatorAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.health.HealthAggregator;
import org.springframework.boot.actuate.health.OrderedHealthAggregator;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.collector.handler.CollectedSpanHandler;
import zipkin2.internal.Nullable;
import zipkin2.server.internal.Access;
import zipkin2.server.internal.ZipkinHttpCollector;
import zipkin2.server.internal.ZipkinServerConfiguration;
import zipkin2.storage.InMemoryStorage;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

// just like brave.features.handler.RedactingFinishedSpanHandlerTest
public class RedactingCollectedSpanHandlerTest {
  /**
   * This is just a dummy pattern. See <a href="https://github.com/ExpediaDotCom/haystack-secrets-commons/blob/master/src/main/java/com/expedia/www/haystack/commons/secretDetector/HaystackCompositeCreditCardFinder.java">HaystackCompositeCreditCardFinder</a>
   * for a realistic one.
   */
  static final Pattern CREDIT_CARD = Pattern.compile("[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}");

  /** Simple example of a replacement pattern, deleting entries which only include credit cards */
  static String maybeUpdateValue(String value) {
    Matcher matcher = CREDIT_CARD.matcher(value);
    if (matcher.find()) {
      String matched = matcher.group(0);
      if (matched.equals(value)) return null;
      return value.replace(matched, "xxxx-xxxx-xxxx-xxxx");
    }
    return value;
  }

  static final class RedactingCollectedSpanHandler implements CollectedSpanHandler {
    @Override @Nullable public Span handle(Span span) {
      Span.Builder b = span.toBuilder().clearAnnotations().clearTags();
      span.annotations().forEach(a -> {
        String value = maybeUpdateValue(a.value());
        if (value != null) b.addAnnotation(a.timestamp(), value);
      });
      span.tags().forEach((k, v) -> {
        String value = maybeUpdateValue(v);
        if (value != null) b.putTag(k, value);
      });
      return b.build();
    }
  }

  AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

  @Test public void showRedaction() {
    Access.collector(context.getBean(ZipkinHttpCollector.class)).accept(asList(
      Span.newBuilder()
        .traceId("a").id("a")
        .putTag("a", "1")
        .putTag("b", "4121-2319-1483-3421")
        .addAnnotation(1L, "cc=4121-2319-1483-3421")
        .putTag("c", "3").build()
    ), Callback.NOOP_VOID);

    assertThat(context.getBean(InMemoryStorage.class).getTraces()).containsExactly(asList(
      Span.newBuilder()
        .traceId("a").id("a")
        .putTag("a", "1")
        // credit card tag was nuked
        .addAnnotation(1L, "cc=xxxx-xxxx-xxxx-xxxx")
        .putTag("c", "3").build()
    ));
  }

  @Before public void setup() {
    context.register(
      ArmeriaSpringActuatorAutoConfiguration.class,
      EndpointAutoConfiguration.class,
      PropertyPlaceholderAutoConfiguration.class,
      Config.class,
      ZipkinServerConfiguration.class,
      ZipkinHttpCollector.class
    );
    context.refresh();
  }

  @After public void close() {
    context.close();
  }

  @Configuration
  public static class Config {
    @Bean CollectedSpanHandler redacter() {
      return new RedactingCollectedSpanHandler();
    }

    @Bean public HealthAggregator healthAggregator() {
      return new OrderedHealthAggregator();
    }

    @Bean MeterRegistry registry() {
      return new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }
  }
}
