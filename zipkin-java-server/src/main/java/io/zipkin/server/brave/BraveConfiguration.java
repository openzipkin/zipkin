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
// TODO: switch package back after https://github.com/openzipkin/brave/pull/99
package io.zipkin.server.brave;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.zipkin.ZipkinSpanCollector;
import java.util.Collections;
import javax.inject.Singleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

@Configuration
@Import({JDBCTracerConfiguration.class, JDBCTracerConfiguration.class})
public class BraveConfiguration {

  /** Lazy because ZipkinSpanCollector makes network connections in its constructor. */
  @Bean
  @Singleton
  @Lazy
  @Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
  ZipkinSpanCollector zipkinSpanCollector(@Value("${zipkin.collector.port:9410}") int scribePort) {
    return new ZipkinSpanCollector("127.0.0.1", scribePort);
  }

  @Bean
  @Singleton
  Brave brave(ZipkinSpanCollector spanCollector) {
    return new Brave.Builder("zipkin-query")
        .traceFilters(Collections.emptyList()) // sample all
        .spanCollector(spanCollector).build();
  }
}
