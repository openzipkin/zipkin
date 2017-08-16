/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;

public class EndpointTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test
  public void messageWhenMissingServiceName() {
    thrown.expect(NullPointerException.class);
    thrown.expectMessage("serviceName");

    Endpoint.builder().ipv4(127 << 24 | 1).build();
  }

  @Test
  public void missingIpv4CoercesTo0() {
    assertThat(Endpoint.builder().serviceName("foo").build().ipv4)
        .isEqualTo(0);
  }

  @Test
  public void builderWithPort_0CoercesToNull() {
    assertThat(Endpoint.builder().serviceName("foo").port(0).build().port)
        .isNull();
  }

  @Test
  public void builderWithPort_highest() {
    short port = Endpoint.builder().serviceName("foo").port(65535).build().port;

    assertThat(port)
        .isEqualTo((short) -1); // an unsigned short of 65535 is the same as -1

    assertThat(port & 0xffff)
        .isEqualTo(65535);
  }

  @Test
  public void builderWithPort_highest_short() {
    short port = Endpoint.builder().serviceName("foo").port(new Short((short) 65535)).build().port;

    assertThat(port)
        .isEqualTo((short) -1); // an unsigned short of 65535 is the same as -1

    assertThat(port & 0xffff)
        .isEqualTo(65535);
  }

  @Test
  public void ip_addr_ipv4() throws UnknownHostException {
    Endpoint.Builder builder = Endpoint.builder().serviceName("foo");
    assertThat(builder.parseIp(Inet4Address.getByName("1.2.3.4"))).isTrue();
    Endpoint endpoint = builder.build();

    assertThat(endpoint.ipv4)
        .isEqualTo(1 << 24 | 2 << 16 | 3 << 8 | 4);
    assertThat(endpoint.ipv6)
        .isNull();
  }

  @Test
  public void ip_string_ipv4() throws UnknownHostException {
    Endpoint.Builder builder = Endpoint.builder().serviceName("foo");
    assertThat(builder.parseIp("1.2.3.4")).isTrue();
    Endpoint endpoint = builder.build();

    assertThat(endpoint.ipv4)
        .isEqualTo(1 << 24 | 2 << 16 | 3 << 8 | 4);
    assertThat(endpoint.ipv6)
        .isNull();
  }

  @Test
  public void ipv6() throws UnknownHostException {
    byte[] ipv6 = Inet6Address.getByName("2001:db8::c001").getAddress();

    Endpoint endpoint = Endpoint.builder().serviceName("foo").ipv6(ipv6).build();

    assertThat(endpoint.ipv4)
        .isEqualTo(0);
    assertThat(endpoint.ipv6)
        .isEqualTo(ipv6);
  }

  @Test
  public void ip_addr_ipv6() throws UnknownHostException {
    InetAddress ipv6 = Inet6Address.getByName("2001:db8::c001");

    Endpoint.Builder builder = Endpoint.builder().serviceName("foo");
    assertThat(builder.parseIp(ipv6)).isTrue();
    Endpoint endpoint = builder.build();

    assertThat(endpoint.ipv4)
        .isEqualTo(0);
    assertThat(endpoint.ipv6)
        .isEqualTo(ipv6.getAddress());
  }

  @Test
  public void ip_string_ipv6() throws UnknownHostException {
    byte[] ipv6 = Inet6Address.getByName("2001:db8::c001").getAddress();

    Endpoint.Builder builder = Endpoint.builder().serviceName("foo");
    assertThat(builder.parseIp("2001:db8::c001")).isTrue();
    Endpoint endpoint = builder.build();

    assertThat(endpoint.ipv4)
        .isEqualTo(0);
    assertThat(endpoint.ipv6)
        .isEqualTo(ipv6);
  }

  @Test
  public void ipv6_mappedIpv4() throws UnknownHostException {
    // ::FFFF:1.2.3.4
    byte[] ipv6_mapped = new byte[16];
    ipv6_mapped[10] = (byte) 0xff;
    ipv6_mapped[11] = (byte) 0xff;
    ipv6_mapped[12] = (byte) 1;
    ipv6_mapped[13] = (byte) 2;
    ipv6_mapped[14] = (byte) 3;
    ipv6_mapped[15] = (byte) 4;

    Endpoint endpoint = Endpoint.builder().serviceName("foo").ipv6(ipv6_mapped).build();

    assertThat(endpoint.ipv4)
        .isEqualTo(1 << 24 | 2 << 16 | 3 << 8 | 4);
    assertThat(endpoint.ipv6)
        .isNull();
  }

  @Test
  public void ip_string_mappedIpv4() throws UnknownHostException {
    Endpoint.Builder builder = Endpoint.builder().serviceName("foo");
    assertThat(builder.parseIp("::FFFF:1.2.3.4")).isTrue();
    Endpoint endpoint = builder.build();

    assertThat(endpoint.ipv4)
        .isEqualTo(1 << 24 | 2 << 16 | 3 << 8 | 4);
    assertThat(endpoint.ipv6)
        .isNull();
  }

  @Test
  public void ip_string_compatIpv4() throws UnknownHostException {
    Endpoint.Builder builder = Endpoint.builder().serviceName("foo");
    assertThat(builder.parseIp("::0000:1.2.3.4")).isTrue();
    Endpoint endpoint = builder.build();

    assertThat(endpoint.ipv4)
        .isEqualTo(1 << 24 | 2 << 16 | 3 << 8 | 4);
    assertThat(endpoint.ipv6)
        .isNull();
  }

  @Test
  public void ipv6_notMappedIpv4() throws UnknownHostException {
    // ::FFEF:1.2.3.4
    byte[] ipv6_mapped = new byte[16];
    ipv6_mapped[10] = (byte) 0xff;
    ipv6_mapped[11] = (byte) 0xef;
    ipv6_mapped[12] = (byte) 1;
    ipv6_mapped[13] = (byte) 2;
    ipv6_mapped[14] = (byte) 3;
    ipv6_mapped[15] = (byte) 4;

    Endpoint endpoint = Endpoint.builder().serviceName("foo").ipv6(ipv6_mapped).build();

    assertThat(endpoint.ipv4)
        .isZero();
    assertThat(endpoint.ipv6)
        .isEqualTo(ipv6_mapped);
  }

  @Test
  public void ipv6_compatIpv4() throws UnknownHostException {
    // ::1.2.3.4
    byte[] ipv6_mapped = new byte[16];
    ipv6_mapped[12] = (byte) 1;
    ipv6_mapped[13] = (byte) 2;
    ipv6_mapped[14] = (byte) 3;
    ipv6_mapped[15] = (byte) 4;

    Endpoint endpoint = Endpoint.builder().serviceName("foo").ipv6(ipv6_mapped).build();

    assertThat(endpoint.ipv4)
        .isEqualTo(1 << 24 | 2 << 16 | 3 << 8 | 4);
    assertThat(endpoint.ipv6)
        .isNull();
  }

  /** This ensures we don't mistake IPv6 localhost for a mapped IPv4 0.0.0.1 */
  @Test
  public void ipv6_localhost() throws UnknownHostException {
    byte[] ipv6_localhost = new byte[16];
    ipv6_localhost[15] = 1;

    Endpoint endpoint = Endpoint.builder().serviceName("foo").ipv6(ipv6_localhost).build();

    assertThat(endpoint.ipv4)
        .isZero();
    assertThat(endpoint.ipv6)
        .isEqualTo(ipv6_localhost);
  }

  /** This ensures we don't mistake IPv6 localhost for a mapped IPv4 0.0.0.1 */
  @Test
  public void ip_string_ipv6_localhost() throws UnknownHostException {
    byte[] ipv6_localhost = new byte[16];
    ipv6_localhost[15] = 1;

    Endpoint.Builder builder = Endpoint.builder().serviceName("foo");
    assertThat(builder.parseIp("::1")).isTrue();
    Endpoint endpoint = builder.build();

    assertThat(endpoint.ipv4)
        .isZero();
    assertThat(endpoint.ipv6)
        .isEqualTo(ipv6_localhost);
  }

  /** This is an unusable compat Ipv4 of 0.0.0.2. This makes sure it isn't mistaken for localhost */
  @Test
  public void ipv6_notLocalhost() throws UnknownHostException {
    byte[] ipv6_localhost = new byte[16];
    ipv6_localhost[15] = 2;

    Endpoint endpoint = Endpoint.builder().serviceName("foo").ipv6(ipv6_localhost).build();

    assertThat(endpoint.ipv4)
        .isEqualTo(2);
    assertThat(endpoint.ipv6)
        .isNull();
  }

  /** The integer arg of port should be a whole number */
  @Test
  public void builderWithPort_negativeIsInvalid() {

    assertThat(Endpoint.builder().serviceName("foo").port(-1).build().port).isNull();
  }

  /** The integer arg of port should fit in a 16bit unsigned value */
  @Test
  public void builderWithPort_tooHighIsInvalid() {
    thrown.expect(IllegalArgumentException.class);
    thrown.expectMessage("invalid port 65536");

    Endpoint.builder().serviceName("foo").port(65536).build();
  }

  @Test
  public void lowercasesServiceName() {
    assertThat(Endpoint.builder().serviceName("fFf").ipv4(127 << 24 | 1).build().serviceName)
        .isEqualTo("fff");
  }

  @Test
  public void testToStringIsJson_minimal() {
    assertThat(Endpoint.builder().serviceName("foo").build())
        .hasToString("{\"serviceName\":\"foo\"}");
  }

  @Test
  public void testToStringIsJson_ipv4() {
    assertThat(Endpoint.builder().serviceName("foo").ipv4(127 << 24 | 1).build())
        .hasToString("{\"serviceName\":\"foo\",\"ipv4\":\"127.0.0.1\"}");
  }

  @Test
  public void testToStringIsJson_ipv4Port() {
    assertThat(Endpoint.builder().serviceName("foo").ipv4(127 << 24 | 1).port(80).build())
        .hasToString("{\"serviceName\":\"foo\",\"ipv4\":\"127.0.0.1\",\"port\":80}");
  }

  @Test
  public void testToStringIsJson_ipv6() throws UnknownHostException {
    assertThat(Endpoint.builder().serviceName("foo")
        .ipv6(Inet6Address.getByName("2001:db8::c001").getAddress()).build())
        .hasToString("{\"serviceName\":\"foo\",\"ipv6\":\"2001:db8::c001\"}");
  }
}
