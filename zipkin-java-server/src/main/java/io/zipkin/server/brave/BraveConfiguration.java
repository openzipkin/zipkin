/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.server.brave;

import com.github.kristofa.brave.Brave;
import io.zipkin.SpanStore;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@Import({ApiTracerConfiguration.class, JDBCTracerConfiguration.class})
@EnableScheduling
public class BraveConfiguration {

  @Autowired
  private SpanStoreSpanCollector spanCollector;

  @Scheduled(fixedDelayString = "${zipkin.collector.delayMillisec:1000}")
  public void flushSpans() {
    this.spanCollector.flush();
  }

  /**
   * @param spanStore lazy to avoid circular reference: the collector uses the same span store as the query api.
   */
  @Bean
  SpanStoreSpanCollector spanCollector(@Lazy SpanStore spanStore) {
    return new SpanStoreSpanCollector(spanStore);
  }

  @Bean
  @Scope
  Brave brave(SpanStoreSpanCollector spanCollector) {
    return new Brave.Builder("zipkin-query")
        .traceFilters(Collections.emptyList()) // sample all
        .spanCollector(spanCollector).build();
  }
}
