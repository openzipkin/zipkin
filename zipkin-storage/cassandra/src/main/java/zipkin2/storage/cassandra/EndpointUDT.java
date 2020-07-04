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
package zipkin2.storage.cassandra;

import com.datastax.driver.mapping.annotations.UDT;
import java.io.Serializable;
import java.net.InetAddress;
import zipkin2.Endpoint;
import zipkin2.internal.Nullable;

import static zipkin2.storage.cassandra.CassandraUtil.inetAddressOrNull;

@UDT(name = "endpoint")
final class EndpointUDT implements Serializable { // for Spark jobs
  static final long serialVersionUID = 0L;

  @Nullable static EndpointUDT create(@Nullable Endpoint endpoint) {
    if (endpoint == null) return null;
    EndpointUDT result = new EndpointUDT();
    result.service = endpoint.serviceName();
    result.ipv4 = inetAddressOrNull(endpoint.ipv4(), endpoint.ipv4Bytes());
    result.ipv6 = inetAddressOrNull(endpoint.ipv6(), endpoint.ipv6Bytes());
    result.port = endpoint.portAsInt();
    return result;
  }

  String service;
  InetAddress ipv4;
  InetAddress ipv6;
  int port;

  EndpointUDT() {
    this.service = null;
    this.ipv4 = null;
    this.ipv6 = null;
    this.port = 0;
  }

  public String getService() {
    return service;
  }

  public InetAddress getIpv4() {
    return ipv4;
  }

  public InetAddress getIpv6() {
    return ipv6;
  }

  public int getPort() {
    return port;
  }

  public void setService(String service) {
    this.service = service;
  }

  public void setIpv4(InetAddress ipv4) {
    this.ipv4 = ipv4;
  }

  public void setIpv6(InetAddress ipv6) {
    this.ipv6 = ipv6;
  }

  public void setPort(int port) {
    this.port = port;
  }

  Endpoint toEndpoint() {
    Endpoint.Builder builder = Endpoint.newBuilder().serviceName(service).port(port);
    builder.parseIp(ipv4);
    builder.parseIp(ipv6);
    return builder.build();
  }

  @Override public String toString() {
    return "EndpointUDT{"
      + "service=" + service + ", "
      + "ipv4=" + ipv4 + ", "
      + "ipv6=" + ipv6 + ", "
      + "port=" + port
      + "}";
  }
}
