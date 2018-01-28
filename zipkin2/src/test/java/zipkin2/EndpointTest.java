/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;

public class EndpointTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test public void missingIpv4IsNull() {
    assertThat(Endpoint.newBuilder().build().ipv4())
      .isNull();
  }

  /** Many getPort operations return -1 by default. Leniently coerse to null. */
  @Test public void newBuilderWithPort_NegativeCoercesToNull() {
    assertThat(Endpoint.newBuilder().port(-1).build().port())
      .isNull();
  }

  @Test public void newBuilderWithPort_0CoercesToNull() {
    assertThat(Endpoint.newBuilder().port(0).build().port())
      .isNull();
  }

  @Test public void newBuilderWithPort_highest() {
    assertThat(Endpoint.newBuilder().port(65535).build().port())
      .isEqualTo(65535);
  }

  @Test public void ip_addr_ipv4() throws UnknownHostException {
    Endpoint.Builder newBuilder = Endpoint.newBuilder();
    assertThat(newBuilder.parseIp(Inet4Address.getByName("1.2.3.4"))).isTrue();
    Endpoint endpoint = newBuilder.build();

    assertThat(endpoint.ipv4())
      .isEqualTo("1.2.3.4");
    assertThat(endpoint.ipv6())
      .isNull();
  }

  @Test
  public void ip_string_ipv4() throws UnknownHostException {
    Endpoint.Builder newBuilder = Endpoint.newBuilder();
    assertThat(newBuilder.parseIp("1.2.3.4")).isTrue();
    Endpoint endpoint = newBuilder.build();

    assertThat(endpoint.ipv4())
      .isEqualTo("1.2.3.4");
    assertThat(endpoint.ipv6())
      .isNull();
  }

  @Test public void ip_ipv6() throws UnknownHostException {
    String ipv6 = "2001:db8::c001";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertThat(endpoint.ipv4())
      .isNull();
    assertThat(endpoint.ipv6())
      .isEqualTo(ipv6);
  }

  @Test public void ip_ipv6_addr() throws UnknownHostException {
    String ipv6 = "2001:db8::c001";
    Endpoint endpoint = Endpoint.newBuilder().ip(Inet6Address.getByName(ipv6)).build();

    assertThat(endpoint.ipv4())
      .isNull();
    assertThat(endpoint.ipv6())
      .isEqualTo(ipv6);
  }

  @Test public void ip_ipv6_mappedIpv4() throws UnknownHostException {
    String ipv6 = "::FFFF:1.2.3.4";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertThat(endpoint.ipv4())
      .isEqualTo("1.2.3.4");
    assertThat(endpoint.ipv6())
      .isNull();
  }

  @Test public void ip_ipv6_addr_mappedIpv4() throws UnknownHostException {
    String ipv6 = "::FFFF:1.2.3.4";
    Endpoint endpoint = Endpoint.newBuilder().ip(Inet6Address.getByName(ipv6)).build();

    assertThat(endpoint.ipv4())
      .isEqualTo("1.2.3.4");
    assertThat(endpoint.ipv6())
      .isNull();
  }

  @Test public void ip_ipv6_compatIpv4() {
    String ipv6 = "::0000:1.2.3.4";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertThat(endpoint.ipv4())
      .isEqualTo("1.2.3.4");
    assertThat(endpoint.ipv6())
      .isNull();
  }

  @Test public void ip_ipv6_addr_compatIpv4() throws UnknownHostException {
    String ipv6 = "::0000:1.2.3.4";
    Endpoint endpoint = Endpoint.newBuilder().ip(Inet6Address.getByName(ipv6)).build();

    assertThat(endpoint.ipv4())
      .isEqualTo("1.2.3.4");
    assertThat(endpoint.ipv6())
      .isNull();
  }

  @Test public void ipv6_notMappedIpv4() throws UnknownHostException {
    String ipv6 = "::ffef:1.2.3.4";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertThat(endpoint.ipv4())
      .isNull();
    assertThat(endpoint.ipv6())
      .isNull();
  }

  @Test public void ipv6_downcases() throws UnknownHostException {
    Endpoint endpoint = Endpoint.newBuilder().ip("2001:DB8::C001").build();

    assertThat(endpoint.ipv6())
      .isEqualTo("2001:db8::c001");
  }

  @Test public void ip_ipv6_compatIpv4_compressed() throws UnknownHostException {
    String ipv6 = "::1.2.3.4";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertThat(endpoint.ipv4())
      .isEqualTo("1.2.3.4");
    assertThat(endpoint.ipv6())
      .isNull();
  }

  /** This ensures we don't mistake IPv6 localhost for a mapped IPv4 0.0.0.1 */
  @Test public void ipv6_localhost() throws UnknownHostException {
    String ipv6 = "::1";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertThat(endpoint.ipv4())
      .isNull();
    assertThat(endpoint.ipv6())
      .isEqualTo(ipv6);
  }

  /** This is an unusable compat Ipv4 of 0.0.0.2. This makes sure it isn't mistaken for localhost */
  @Test public void ipv6_notLocalhost() throws UnknownHostException {
    String ipv6 = "::2";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertThat(endpoint.ipv4())
      .isNull();
    assertThat(endpoint.ipv6())
      .isEqualTo(ipv6);
  }

  /** The integer arg of port should be a whole number */
  @Test public void newBuilderWithPort_tooLargeIsInvalid() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("invalid port 65536");

    assertThat(Endpoint.newBuilder().port(65536).build().port()).isNull();
  }

  /** The integer arg of port should fit in a 16bit unsigned value */
  @Test public void newBuilderWithPort_tooHighIsInvalid() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("invalid port 65536");

    Endpoint.newBuilder().port(65536).build();
  }

  @Test public void lowercasesServiceName() {
    assertThat(Endpoint.newBuilder().serviceName("fFf").ip("127.0.0.1").build().serviceName())
      .isEqualTo("fff");
  }
}
