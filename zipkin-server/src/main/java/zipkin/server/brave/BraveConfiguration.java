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

import com.github.kristofa.brave.Brave;
import com.github.kristofa.brave.ServerTracer;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import zipkin.CollectorMetrics;
import zipkin.CollectorSampler;
import zipkin.Endpoint;
import zipkin.StorageComponent;

@Configuration
@ConditionalOnClass(ServerTracer.class)
@ConditionalOnProperty(name = "zipkin.self-tracing.enabled", havingValue = "true")
@Import({ApiTracerConfiguration.class, JDBCTracerConfiguration.class})
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
    return Endpoint.create("zipkin-server", ipv4, port);
  }

  @Bean LocalSpanCollector spanCollector(StorageComponent storage,
      @Value("${zipkin.self-tracing.flush-interval:1}") int flushInterval,
      CollectorSampler sampler, CollectorMetrics metrics) {
    return new LocalSpanCollector(storage, flushInterval, sampler, metrics);
  }

  @Bean
  @Scope Brave brave(@Qualifier("local") Endpoint localEndpoint,
      LocalSpanCollector spanCollector) {
    return new Brave.Builder(localEndpoint.ipv4, localEndpoint.port, localEndpoint.serviceName)
        .spanCollector(spanCollector).build();
  }
}
