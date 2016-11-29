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
package zipkin.storage.elasticsearch.http;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.net.InetAddresses;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import okhttp3.Dns;
import okhttp3.HttpUrl;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * This returns a Dns provider that combines the IPv4 or IPv6 addresses from a supplied list of
 * urls, provided they are all http and share the same port.
 */
final class PseudoAddressRecordSet {

  static Dns create(List<String> urls, Dns actualDns) {
    Set<String> schemes = Sets.newLinkedHashSet();
    Set<String> hosts = Sets.newLinkedHashSet();
    Set<InetAddress> ipAddresses = Sets.newLinkedHashSet();
    Set<Integer> ports = Sets.newLinkedHashSet();

    for (String url : urls) {
      HttpUrl httpUrl = HttpUrl.parse(url);
      schemes.add(httpUrl.scheme());
      if (InetAddresses.isInetAddress(httpUrl.host())) {
        ipAddresses.add(InetAddresses.forString(httpUrl.host()));
      } else {
        hosts.add(httpUrl.host());
      }
      ports.add(httpUrl.port());
    }

    checkArgument(ports.size() == 1, "Only one port supported with multiple hosts %s", urls);
    checkArgument(schemes.size() == 1 && schemes.iterator().next().equals("http"),
        "Only http supported with multiple hosts %s", urls);

    if (hosts.isEmpty()) return new StaticDns(ipAddresses);
    return new ConcatenatingDns(ipAddresses, hosts, actualDns);
  }

  static final class StaticDns implements Dns {
    private final List<InetAddress> ipAddresses;

    StaticDns(Set<InetAddress> ipAddresses) {
      this.ipAddresses = ImmutableList.copyOf(ipAddresses);
    }

    @Override public List<InetAddress> lookup(String hostname) {
      return ipAddresses;
    }

    @Override public String toString() {
      return "StaticDns(" + ipAddresses + ")";
    }
  }

  static final class ConcatenatingDns implements Dns {
    final Set<InetAddress> ipAddresses;
    final Set<String> hosts;
    final Dns actualDns;

    ConcatenatingDns(Set<InetAddress> ipAddresses, Set<String> hosts, Dns actualDns) {
      this.ipAddresses = ipAddresses;
      this.hosts = hosts;
      this.actualDns = actualDns;
    }

    @Override public List<InetAddress> lookup(String hostname) throws UnknownHostException {
      ImmutableList.Builder<InetAddress> result = ImmutableList.builder();
      result.addAll(ipAddresses);
      for (String host : hosts) {
        result.addAll(actualDns.lookup(host));
      }
      return result.build();
    }

    @Override public String toString() {
      return "ConcatenatingDns(" + ipAddresses + "," + hosts + ")";
    }
  }
}
