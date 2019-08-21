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
import com.linecorp.armeria.common.util.Exceptions;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.function.Supplier;

// TODO: testme
final class InitialEndpointSupplier implements Supplier<EndpointGroup> {
  final String hosts;
  final SessionProtocol sessionProtocol;

  InitialEndpointSupplier(SessionProtocol sessionProtocol, String hosts) {
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
      return resolveDnsAddresses(host, port);
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
        endpointGroups.add(resolveDnsAddresses(url.getHost(), getPort(url)));
      }
    }

    if (!staticEndpoints.isEmpty()) {
      endpointGroups.add(new StaticEndpointGroup(staticEndpoints));
    }

    return endpointGroups.size() == 1 ? endpointGroups.get(0)
      : new CompositeEndpointGroup(endpointGroups);
  }

  // Rather than result in an empty group. Await DNS resolution as this call is deferred anyway
  DnsAddressEndpointGroup resolveDnsAddresses(String host, int port) {
    DnsAddressEndpointGroup result = new DnsAddressEndpointGroupBuilder(host).port(port).build();
    try {
      result.awaitInitialEndpoints(1, TimeUnit.SECONDS);
    } catch (Exception e) {
      // let it fail later
    }
    return result;
  }

  int getPort(URI url) {
    int port = url.getPort();
    if (port == -1) port = sessionProtocol.defaultPort();
    return port;
  }

  // TODO(anuraaga): Move this upstream - https://github.com/line/armeria/issues/1897
  static class CompositeEndpointGroup extends AbstractListenable<List<Endpoint>>
    implements EndpointGroup {

    static final AtomicIntegerFieldUpdater<CompositeEndpointGroup> dirtyUpdater =
      AtomicIntegerFieldUpdater.newUpdater(CompositeEndpointGroup.class, "dirty");

    final List<EndpointGroup> endpointGroups;
    final CompletableFuture<List<Endpoint>> initialEndpointsFuture;

    volatile List<Endpoint> merged = Collections.emptyList();
    volatile int dirty = 1;

    CompositeEndpointGroup(List<EndpointGroup> endpointGroups) {
      this.endpointGroups = endpointGroups;
      for (EndpointGroup group : endpointGroups) {
        group.addListener(unused -> {
          dirtyUpdater.set(this, 1);
          notifyListeners(endpoints());
        });
      }

      initialEndpointsFuture = CompletableFuture.anyOf(
        endpointGroups.stream()
          .map(EndpointGroup::initialEndpointsFuture)
          .toArray(CompletableFuture[]::new))
        .thenApply(unused -> endpoints());
    }

    @Override public List<Endpoint> endpoints() {
      if (dirty == 0) {
        return merged;
      }

      if (!dirtyUpdater.compareAndSet(this, 1, 0)) {
        // Another thread might be updating merged at this time, but endpoint groups are allowed to take a
        // little bit of time to reflect updates.
        return merged;
      }

      List<Endpoint> newEndpoints = new ArrayList<>();
      for (EndpointGroup endpointGroup : endpointGroups) {
        newEndpoints.addAll(endpointGroup.endpoints());
      }

      return merged = Collections.unmodifiableList(newEndpoints);
    }

    @Override public CompletableFuture<List<Endpoint>> initialEndpointsFuture() {
      return initialEndpointsFuture;
    }

    @Override public void close() {
      Throwable t = null;
      for (EndpointGroup endpointGroup : endpointGroups) {
        try {
          endpointGroup.close();
        } catch (Throwable thrown) {
          if (t == null) {
            t = thrown;
          }
        }
      }
      if (t != null) {
        Exceptions.throwUnsafely(t);
      }
    }

    @Override public String toString() {
      return "Composite{" + endpointGroups + "}";
    }
  }

  static boolean isIpAddress(String address) {
    return zipkin2.Endpoint.newBuilder().parseIp(address);
  }

  @Override public String toString() {
    return hosts;
  }
}
