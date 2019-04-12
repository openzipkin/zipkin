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
package zipkin2.storage.cassandra.internal;

import zipkin2.Endpoint;

// Similar to com.google.common.net.HostAndPort, but no guava dep
public final class HostAndPort {
  final String host;
  final int port;

  HostAndPort(String host, int port) {
    this.host = host;
    this.port = port;
  }

  /** Returns the unvalidated hostname or IP literal */
  public String getHost() {
    return host;
  }

  /** Returns the port */
  public int getPort() {
    return port;
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof HostAndPort)) return false;
    HostAndPort that = (HostAndPort) o;
    return host.equals(that.host) && port == that.port;
  }

  @Override public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (host == null) ? 0 : host.hashCode();
    h *= 1000003;
    h ^= port;
    return h;
  }

  @Override public String toString() {
    return "HostAndPort{host=" + host + ", port=" + port + "}";
  }

  /**
   * Constructs a host-port pair from the given string, defaulting to the indicated port if absent
   */
  public static HostAndPort fromString(String hostPort, int defaultPort) {
    if (hostPort == null) throw new NullPointerException("hostPort == null");

    String host = hostPort;
    int endHostIndex = hostPort.length();
    if (hostPort.startsWith("[")) { // Bracketed IPv6
      endHostIndex = hostPort.lastIndexOf(']') + 1;
      host = hostPort.substring(1, endHostIndex == 0 ? 1 : endHostIndex - 1);
      if (!Endpoint.newBuilder().parseIp(host)) { // reuse our IPv6 validator
        throw new IllegalArgumentException(hostPort + " contains an invalid IPv6 literal");
      }
    } else {
      int colonIndex = hostPort.indexOf(':'), nextColonIndex = hostPort.lastIndexOf(':');
      if (colonIndex >= 0) {
        if (colonIndex == nextColonIndex) { // only 1 colon
          host = hostPort.substring(0, colonIndex);
          endHostIndex = colonIndex;
        } else if (!Endpoint.newBuilder().parseIp(hostPort)) { // reuse our IPv6 validator
          throw new IllegalArgumentException(hostPort + " is an invalid IPv6 literal");
        }
      }
    }
    if (host.isEmpty()) throw new IllegalArgumentException(hostPort + " has an empty host");
    if (endHostIndex + 1 < hostPort.length() && hostPort.charAt(endHostIndex) == ':') {
      return new HostAndPort(host, validatePort(hostPort.substring(endHostIndex + 1), hostPort));
    }
    return new HostAndPort(host, defaultPort);
  }

  static int validatePort(String portString, String hostPort) {
    for (int i = 0, length = portString.length(); i < length; i++) {
      char c = portString.charAt(i);
      if (c >= '0' && c <= '9') continue; // isDigit
      throw new IllegalArgumentException(hostPort + " has an invalid port");
    }
    int result = Integer.parseInt(portString);
    if (result == 0 || result > 0xffff) {
      throw new IllegalArgumentException(hostPort + " has an invalid port");
    }
    return result;
  }
}
