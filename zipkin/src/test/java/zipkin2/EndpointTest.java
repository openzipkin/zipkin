/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2;

import java.net.Inet4Address;
import java.net.Inet6Address;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EndpointTest {

  @Test void missingIpv4IsNull() {
    assertThat(Endpoint.newBuilder().build().ipv4())
      .isNull();
  }

  /** Many getPort operations return -1 by default. Leniently coerse to null. */
  @Test void newBuilderWithPort_NegativeCoercesToNull() {
    assertThat(Endpoint.newBuilder().port(-1).build().port())
      .isNull();
  }

  @Test void newBuilderWithPort_0CoercesToNull() {
    assertThat(Endpoint.newBuilder().port(0).build().port())
      .isNull();
  }

  @Test void newBuilderWithPort_highest() {
    assertThat(Endpoint.newBuilder().port(65535).build().port())
      .isEqualTo(65535);
  }

  @Test void ip_addr_ipv4() throws Exception {
    Endpoint.Builder newBuilder = Endpoint.newBuilder();
    assertThat(newBuilder.parseIp(Inet4Address.getByName("43.0.192.2"))).isTrue();
    Endpoint endpoint = newBuilder.build();

    assertExpectedIpv4(endpoint);
  }

  @Test void ip_bytes_ipv4() throws Exception {
    Endpoint.Builder newBuilder = Endpoint.newBuilder();
    assertThat(newBuilder.parseIp(Inet4Address.getByName("43.0.192.2").getAddress())).isTrue();
    Endpoint endpoint = newBuilder.build();

    assertExpectedIpv4(endpoint);
  }

  @Test void ip_string_ipv4() {
    Endpoint.Builder newBuilder = Endpoint.newBuilder();
    assertThat(newBuilder.parseIp("43.0.192.2")).isTrue();
    Endpoint endpoint = newBuilder.build();

    assertExpectedIpv4(endpoint);
  }

  @Test void ip_ipv6() throws Exception {
    String ipv6 = "2001:db8::c001";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertThat(endpoint.ipv4())
      .isNull();
    assertThat(endpoint.ipv4Bytes())
      .isNull();
    assertThat(endpoint.ipv6())
      .isEqualTo(ipv6);
    assertThat(endpoint.ipv6Bytes())
      .containsExactly(Inet6Address.getByName(ipv6).getAddress());
  }

  @Test void ip_ipv6_addr() throws Exception {
    String ipv6 = "2001:db8::c001";
    Endpoint endpoint = Endpoint.newBuilder().ip(Inet6Address.getByName(ipv6)).build();

    assertThat(endpoint.ipv4())
      .isNull();
    assertThat(endpoint.ipv4Bytes())
      .isNull();
    assertThat(endpoint.ipv6())
      .isEqualTo(ipv6);
    assertThat(endpoint.ipv6Bytes())
      .containsExactly(Inet6Address.getByName(ipv6).getAddress());
  }

  @Test void parseIp_ipv6_bytes() throws Exception {
    String ipv6 = "2001:db8::c001";

    Endpoint.Builder newBuilder = Endpoint.newBuilder();
    assertThat(newBuilder.parseIp(Inet6Address.getByName(ipv6))).isTrue();
    Endpoint endpoint = newBuilder.build();

    assertThat(endpoint.ipv4())
      .isNull();
    assertThat(endpoint.ipv4Bytes())
      .isNull();
    assertThat(endpoint.ipv6())
      .isEqualTo(ipv6);
    assertThat(endpoint.ipv6Bytes())
      .containsExactly(Inet6Address.getByName(ipv6).getAddress());
  }

  @Test void ip_ipv6_mappedIpv4() {
    String ipv6 = "::FFFF:43.0.192.2";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertExpectedIpv4(endpoint);
  }

  @Test void ip_ipv6_addr_mappedIpv4() throws Exception {
    String ipv6 = "::FFFF:43.0.192.2";
    Endpoint endpoint = Endpoint.newBuilder().ip(Inet6Address.getByName(ipv6)).build();

    assertExpectedIpv4(endpoint);
  }

  @Test void ip_ipv6_compatIpv4() {
    String ipv6 = "::0000:43.0.192.2";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertExpectedIpv4(endpoint);
  }

  @Test void ip_ipv6_addr_compatIpv4() throws Exception {
    String ipv6 = "::0000:43.0.192.2";
    Endpoint endpoint = Endpoint.newBuilder().ip(Inet6Address.getByName(ipv6)).build();

    assertExpectedIpv4(endpoint);
  }

  @Test void ipv6_notMappedIpv4() {
    String ipv6 = "::ffef:43.0.192.2";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertThat(endpoint.ipv4())
      .isNull();
    assertThat(endpoint.ipv4Bytes())
      .isNull();
    assertThat(endpoint.ipv6())
      .isNull();
    assertThat(endpoint.ipv6Bytes())
      .isNull();
  }

  @Test void ipv6_downcases() {
    Endpoint endpoint = Endpoint.newBuilder().ip("2001:DB8::C001").build();

    assertThat(endpoint.ipv6())
      .isEqualTo("2001:db8::c001");
  }

  @Test void ip_ipv6_compatIpv4_compressed() {
    String ipv6 = "::43.0.192.2";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertExpectedIpv4(endpoint);
  }

  /** This ensures we don't mistake IPv6 localhost for a mapped IPv4 0.0.0.1 */
  @Test void ipv6_localhost() {
    String ipv6 = "::1";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertThat(endpoint.ipv4())
      .isNull();
    assertThat(endpoint.ipv4Bytes())
      .isNull();
    assertThat(endpoint.ipv6())
      .isEqualTo(ipv6);
    assertThat(endpoint.ipv6Bytes())
      .containsExactly(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
  }

  /** This is an unusable compat Ipv4 of 0.0.0.2. This makes sure it isn't mistaken for localhost */
  @Test void ipv6_notLocalhost() {
    String ipv6 = "::2";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertThat(endpoint.ipv4())
      .isNull();
    assertThat(endpoint.ipv6())
      .isEqualTo(ipv6);
  }

  /** The integer arg of port should be a whole number */
  @Test void newBuilderWithPort_tooLargeIsInvalid() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
      assertThat(Endpoint.newBuilder().port(65536).build().port()).isNull();
    });
    assertThat(exception.getMessage()).contains("invalid port 65536");
  }

  /**
   * The integer arg of port should fit in a 16bit unsigned value
   */
  @Test void newBuilderWithPort_tooHighIsInvalid() {
    Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
      Endpoint.newBuilder().port(65536).build();
    });
    assertThat(exception.getMessage()).contains("invalid port 65536");
  }

  /** Catches common error when zero is passed instead of null for a port */
  @Test void coercesZeroPortToNull() {
    Endpoint endpoint = Endpoint.newBuilder().port(0).build();

    assertThat(endpoint.port())
      .isNull();
  }

  @Test void lowercasesServiceName() {
    assertThat(Endpoint.newBuilder().serviceName("fFf").ip("127.0.0.1").build().serviceName())
      .isEqualTo("fff");
  }

  static void assertExpectedIpv4(Endpoint endpoint) {
    assertThat(endpoint.ipv4())
      .isEqualTo("43.0.192.2");
    assertThat(endpoint.ipv4Bytes())
      .containsExactly(43, 0, 192, 2);
    assertThat(endpoint.ipv6())
      .isNull();
    assertThat(endpoint.ipv6Bytes())
      .isNull();
  }
}
