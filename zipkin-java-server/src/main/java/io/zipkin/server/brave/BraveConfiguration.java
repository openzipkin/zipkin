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

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerTracer;

import io.zipkin.Endpoint;
import io.zipkin.SpanStore;

@Configuration
@ConditionalOnClass(ServerTracer.class)
@Import({ApiTracerConfiguration.class, JDBCTracerConfiguration.class})
@EnableScheduling
public class BraveConfiguration {

  @Autowired
  private SpanStoreSpanCollector spanCollector;

  @Scheduled(fixedDelayString = "${zipkin.collector.delayMillisec:1000}")
  public void flushSpans() {
    this.spanCollector.flush();
  }

  /** This gets the lanIP without trying to lookup its name. */
  // http://stackoverflow.com/questions/8765578/get-local-ip-address-without-connecting-to-the-internet
  @Bean
  @Scope
  Endpoint local(@Value("${server.port}") int port) {
    int ipv4;
    try {
      ipv4 = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
          .flatMap(i -> Collections.list(i.getInetAddresses()).stream())
          .filter(ip -> ip instanceof Inet4Address && ip.isSiteLocalAddress())
          .map(InetAddress::getAddress)
          .map(bytes -> new BigInteger(bytes).intValue())
          .findAny().get();
    } catch (Exception ignored) {
      ipv4 = 127 << 24 | 1;
    }
    return Endpoint.create("zipkin-query", ipv4, port);
  }

  /**
   * @param spanStore lazy to avoid circular reference: the collector uses the same span store as
   * the query api.
   */
  @Bean
  SpanStoreSpanCollector spanCollector(@Lazy SpanStore spanStore) {
    return new SpanStoreSpanCollector(spanStore);
  }

  @Bean
  @Scope
  Brave brave(@Qualifier("local") Endpoint localEndpoint, SpanStoreSpanCollector spanCollector) {
    return new Brave.Builder(localEndpoint.ipv4, localEndpoint.port, localEndpoint.serviceName)
        .traceFilters(Collections.emptyList()) // sample all
        .spanCollector(spanCollector).build();
  }
}
