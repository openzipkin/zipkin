/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.server.brave;

import com.github.kristofa.brave.BoundarySampler;
import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.ServerClientAndLocalSpanState;
import com.github.kristofa.brave.SpanCollectorMetricsHandler;
import com.github.kristofa.brave.ThreadLocalServerClientAndLocalSpanState;
import com.github.kristofa.brave.local.LocalSpanCollector;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import zipkin.Endpoint;
import zipkin.collector.CollectorMetrics;
import zipkin.server.ConditionalOnSelfTracing;
import zipkin.storage.StorageComponent;

@Configuration
@ConditionalOnSelfTracing
@Import(ApiTracerConfiguration.class)
public class BraveConfiguration {

  /** This gets the lanIP without trying to lookup its name. */
  // http://stackoverflow.com/questions/8765578/get-local-ip-address-without-connecting-to-the-internet
  @Bean
  @Scope Endpoint local(@Value("${server.port:9411}") int port) {
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
    return Endpoint.builder().serviceName("zipkin-server").ipv4(ipv4).port(port).build();
  }

  @Bean LocalSpanCollector spanCollector(StorageComponent storage,
      @Value("${zipkin.self-tracing.flush-interval:1}") int flushInterval,
      final CollectorMetrics metrics) {
    LocalSpanCollector.Config config = LocalSpanCollector.Config.builder()
        .flushInterval(flushInterval).build();
    return LocalSpanCollector.create(storage, config, new SpanCollectorMetricsHandler() {
      CollectorMetrics local = metrics.forTransport("local");

      @Override public void incrementAcceptedSpans(int i) {
        local.incrementSpans(i);
      }

      @Override public void incrementDroppedSpans(int i) {
        local.incrementSpansDropped(i);
      }
    });
  }

  @Bean ServerClientAndLocalSpanState braveState(@Qualifier("local") Endpoint localEndpoint) {
    return new ThreadLocalServerClientAndLocalSpanState(localEndpoint.ipv4, localEndpoint.port,
        localEndpoint.serviceName);
  }

  @Bean Brave brave(ServerClientAndLocalSpanState braveState, LocalSpanCollector spanCollector,
      @Value("${zipkin.self-tracing.sample-rate:1.0}") float rate) {
    return new Brave.Builder(braveState)
        .traceSampler(rate < 0.01 ? BoundarySampler.create(rate) : Sampler.create(rate))
        .spanCollector(spanCollector)
        .build();
  }
}
