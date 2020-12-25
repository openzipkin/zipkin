/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup;
import com.linecorp.armeria.common.SessionProtocol;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.internal.Nullable;

final class InitialEndpointSupplier implements Supplier<EndpointGroup> {
  static final Logger LOGGER = LoggerFactory.getLogger(InitialEndpointSupplier.class);

  final String hosts;
  final SessionProtocol sessionProtocol;

  InitialEndpointSupplier(SessionProtocol sessionProtocol, @Nullable String hosts) {
    if (sessionProtocol == null) throw new NullPointerException("sessionProtocol == null");
    this.sessionProtocol = sessionProtocol;
    this.hosts =
      hosts == null || hosts.isEmpty() ? sessionProtocol.uriText() + "://localhost:9200" : hosts;
  }

  @Override public EndpointGroup get() {
    List<EndpointGroup> endpointGroups = new ArrayList<>();
    for (String hostText : hosts.split(",", 100)) {
      if ("".equals(hostText)) continue; // possibly extra comma

      URI url;
      if (hostText.startsWith("http://") || hostText.startsWith("https://")) {
        url = URI.create(hostText);
      } else if (!sessionProtocol.isTls() && hostText.indexOf(':') == -1) {
        url = URI.create(sessionProtocol.uriText() + "://" + hostText + ":9200");
      } else {
        url = URI.create(sessionProtocol.uriText() + "://" + hostText);
      }

      String host = url.getHost();
      if (host == null) {
        LOGGER.warn("Skipping invalid ES host {}", url);
        continue;
      }

      int port = getPort(url);

      if (port == 9300) {
        LOGGER.warn("Native transport no longer supported. Changing {} to http port 9200", host);
        port = 9200;
      }

      if (isIpAddress(host) || host.equals("localhost")) {
        endpointGroups.add(EndpointGroup.of(Endpoint.of(host, port)));
      } else {
        // A host that isn't an IP may resolve to multiple IP addresses, so we use a endpoint
        // group to round-robin over them. Users can mix addresses that resolve to multiple IPs
        // with single IPs freely, they'll all get used.
        endpointGroups.add(DnsAddressEndpointGroup.builder(host).port(port).build());
      }
    }

    if (endpointGroups.isEmpty()) {
      throw new IllegalArgumentException("No valid endpoints found in ES hosts: " + hosts);
    }

    return EndpointGroup.of(endpointGroups);
  }

  int getPort(URI url) {
    int port = url.getPort();
    return port != -1 ? port : sessionProtocol.defaultPort();
  }

  static boolean isIpAddress(String address) {
    return zipkin2.Endpoint.newBuilder().parseIp(address);
  }

  @Override public String toString() {
    return hosts;
  }
}
