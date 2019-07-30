/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
import com.linecorp.armeria.client.endpoint.StaticEndpointGroup;
import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroup;
import com.linecorp.armeria.client.endpoint.dns.DnsAddressEndpointGroupBuilder;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.util.AbstractListenable;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// TODO: testme
final class StaticEndpointGroupSupplier implements Supplier<EndpointGroup> {
  final String hosts;
  final SessionProtocol sessionProtocol;

  StaticEndpointGroupSupplier(SessionProtocol sessionProtocol, String hosts) {
    this.hosts = hosts == null || hosts.isEmpty() ? "localhost:9200" : hosts;
    this.sessionProtocol = sessionProtocol;
  }

  @Override public EndpointGroup get() {
    List<URI> initialURLs = HostsConverter.convert(hosts);
    if (initialURLs.size() == 1) {
      URI url = initialURLs.get(0);
      String host = url.getHost();
      int port = getPort(url);
      if (isIpAddress(host) || host.equals("localhost")) {
        return new StaticEndpointGroup(Endpoint.of(host, port));
      }
      // A host that isn't an IP may resolve to multiple IP addresses, so we use a endpoint group
      // to round-robin over them.
      return new DnsAddressEndpointGroupBuilder(host).port(port).build();
    }

    List<EndpointGroup> endpointGroups = new ArrayList<>();
    List<Endpoint> staticEndpoints = new ArrayList<>();
    for (URI url : initialURLs) {
      Endpoint endpoint = Endpoint.parse(url.getAuthority());
      if (isIpAddress(url.getHost())) {
        staticEndpoints.add(endpoint);
      } else {
        // A host that isn't an IP may resolve to multiple IP addresses, so we use a endpoint
        // group to round-robin over them. Users can mix addresses that resolve to multiple IPs
        // with single IPs freely, they'll all get used.
        endpointGroups.add(DnsAddressEndpointGroup.of(url.getHost(), getPort(url)));
      }
    }

    if (!staticEndpoints.isEmpty()) {
      endpointGroups.add(new StaticEndpointGroup(staticEndpoints));
    }

    return endpointGroups.size() == 1 ? endpointGroups.get(0)
      : new CompositeEndpointGroup(endpointGroups);
  }

  int getPort(URI url) {
    int port = url.getPort();
    if (port == -1) port = sessionProtocol.defaultPort();
    return port;
  }

  // TODO(anuraaga): Move this upstream - https://github.com/line/armeria/issues/1897
  static class CompositeEndpointGroup extends AbstractListenable<List<Endpoint>>
    implements EndpointGroup {

    final List<EndpointGroup> endpointGroups;

    CompositeEndpointGroup(List<EndpointGroup> endpointGroups) {
      this.endpointGroups = endpointGroups;
      for (EndpointGroup group : endpointGroups) {
        group.addListener(unused -> notifyListeners(endpoints()));
      }
    }

    @Override public List<Endpoint> endpoints() {
      List<Endpoint> merged = new ArrayList<>();
      for (EndpointGroup group : endpointGroups) merged.addAll(group.endpoints());
      return merged;
    }
  }

  static boolean isIpAddress(String address) {
    return zipkin2.Endpoint.newBuilder().parseIp(address);
  }

  @Override public String toString() {
    return "StaticEndpointGroupSupplier{hosts=" + hosts + "}";
  }
}
