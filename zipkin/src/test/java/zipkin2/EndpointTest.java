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
package zipkin2;

import com.diffblue.deeptestutils.Reflector;
import java.net.Inet4Address;
import java.net.Inet6Address;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.powermock.api.mockito.PowerMockito;
import zipkin2.Endpoint.Builder;
import zipkin2.Endpoint.IpFamily;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;

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

  @Test public void ip_addr_ipv4() throws Exception {
    Endpoint.Builder newBuilder = Endpoint.newBuilder();
    assertThat(newBuilder.parseIp(Inet4Address.getByName("43.0.192.2"))).isTrue();
    Endpoint endpoint = newBuilder.build();

    assertExpectedIpv4(endpoint);
  }

  @Test public void ip_bytes_ipv4() throws Exception {
    Endpoint.Builder newBuilder = Endpoint.newBuilder();
    assertThat(newBuilder.parseIp(Inet4Address.getByName("43.0.192.2").getAddress())).isTrue();
    Endpoint endpoint = newBuilder.build();

    assertExpectedIpv4(endpoint);
  }

  @Test public void ip_string_ipv4() {
    Endpoint.Builder newBuilder = Endpoint.newBuilder();
    assertThat(newBuilder.parseIp("43.0.192.2")).isTrue();
    Endpoint endpoint = newBuilder.build();

    assertExpectedIpv4(endpoint);
  }

  @Test public void ip_ipv6() throws Exception {
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

  @Test public void ip_ipv6_addr() throws Exception {
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

  @Test public void parseIp_ipv6_bytes() throws Exception {
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

  @Test public void ip_ipv6_mappedIpv4() {
    String ipv6 = "::FFFF:43.0.192.2";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertExpectedIpv4(endpoint);
  }

  @Test public void ip_ipv6_addr_mappedIpv4() throws Exception {
    String ipv6 = "::FFFF:43.0.192.2";
    Endpoint endpoint = Endpoint.newBuilder().ip(Inet6Address.getByName(ipv6)).build();

    assertExpectedIpv4(endpoint);
  }

  @Test public void ip_ipv6_compatIpv4() {
    String ipv6 = "::0000:43.0.192.2";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertExpectedIpv4(endpoint);
  }

  @Test public void ip_ipv6_addr_compatIpv4() throws Exception {
    String ipv6 = "::0000:43.0.192.2";
    Endpoint endpoint = Endpoint.newBuilder().ip(Inet6Address.getByName(ipv6)).build();

    assertExpectedIpv4(endpoint);
  }

  @Test public void ipv6_notMappedIpv4() {
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

  @Test public void ipv6_downcases() {
    Endpoint endpoint = Endpoint.newBuilder().ip("2001:DB8::C001").build();

    assertThat(endpoint.ipv6())
      .isEqualTo("2001:db8::c001");
  }

  @Test public void ip_ipv6_compatIpv4_compressed() {
    String ipv6 = "::43.0.192.2";
    Endpoint endpoint = Endpoint.newBuilder().ip(ipv6).build();

    assertExpectedIpv4(endpoint);
  }

  /** This ensures we don't mistake IPv6 localhost for a mapped IPv4 0.0.0.1 */
  @Test public void ipv6_localhost() {
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
  @Test public void ipv6_notLocalhost() {
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

  /** Catches common error when zero is passed instead of null for a port */
  @Test public void coercesZeroPortToNull() {
    Endpoint endpoint = Endpoint.newBuilder().port(0).build();

    assertThat(endpoint.port())
      .isNull();
  }

  @Test public void lowercasesServiceName() {
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

  // Test written by Diffblue Cover.
  @Test
  public void ipInputNotNullOutputNotNull() {
    // Arrange
    final Builder objectUnderTest = new Builder();
    objectUnderTest.port = 0;
    objectUnderTest.ipv4Bytes = null;
    objectUnderTest.ipv6 = null;
    objectUnderTest.serviceName = null;
    objectUnderTest.ipv6Bytes = null;
    objectUnderTest.ipv4 = null;
    final InetAddress addr = PowerMockito.mock(InetAddress.class);
    // Act
    final Builder retval = objectUnderTest.ip(addr);
    // Assert result
    Assert.assertNotNull(retval);
    Assert.assertEquals(0, retval.port);
    Assert.assertNull(retval.ipv4Bytes);
    Assert.assertNull(retval.ipv6);
    Assert.assertNull(retval.serviceName);
    Assert.assertNull(retval.ipv6Bytes);
    Assert.assertNull(retval.ipv4);
  }

  // Test written by Diffblue Cover.
  @Test
  public void parseIpInput0OutputFalse() {
    // Arrange
    final Builder objectUnderTest = new Builder();
    objectUnderTest.port = 0;
    objectUnderTest.ipv4Bytes = null;
    objectUnderTest.ipv6 = null;
    objectUnderTest.serviceName = null;
    objectUnderTest.ipv6Bytes = null;
    objectUnderTest.ipv4 = null;
    final byte[] ipBytes = {};
    // Act
    final boolean retval = objectUnderTest.parseIp(ipBytes);
    // Assert result
    Assert.assertFalse(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void portInputNotNullOutputIllegalArgumentException() {
    // Arrange
    final Builder objectUnderTest = new Builder();
    final Integer port = new Integer(65_536);
    // Act
    thrown.expect(IllegalArgumentException.class);
    objectUnderTest.port(port);
    // Method is not expected to return due to exception thrown
  }

  // Test written by Diffblue Cover.
  @Test
  public void portInputNotNullOutputNotNull() {
    // Arrange
    final Builder objectUnderTest = new Builder();
    final Integer port = new Integer(1);
    // Act
    final Builder retval = objectUnderTest.port(port);
    // Assert side effects
    Assert.assertEquals(1, objectUnderTest.port);
    // Assert result
    Assert.assertNotNull(retval);
    Assert.assertEquals(1, retval.port);
    Assert.assertNull(retval.ipv4Bytes);
    Assert.assertNull(retval.ipv6);
    Assert.assertNull(retval.serviceName);
    Assert.assertNull(retval.ipv6Bytes);
    Assert.assertNull(retval.ipv4);
  }

  // Test written by Diffblue Cover.
  @Test
  public void portInputNotNullOutputNotNull2() {
    // Arrange
    final Builder objectUnderTest = new Builder();
    final Integer port = new Integer(-37_749_736);
    // Act
    final Builder retval = objectUnderTest.port(port);
    // Assert result
    Assert.assertNotNull(retval);
    Assert.assertEquals(0, retval.port);
    Assert.assertNull(retval.ipv4Bytes);
    Assert.assertNull(retval.ipv6);
    Assert.assertNull(retval.serviceName);
    Assert.assertNull(retval.ipv6Bytes);
    Assert.assertNull(retval.ipv4);
  }

  // Test written by Diffblue Cover.
  @Test
  public void portInputNullOutputNotNull() {
    // Arrange
    final Builder objectUnderTest = new Builder();
    final Integer port = null;
    // Act
    final Builder retval = objectUnderTest.port(port);
    // Assert result
    Assert.assertNotNull(retval);
    Assert.assertEquals(0, retval.port);
    Assert.assertNull(retval.ipv4Bytes);
    Assert.assertNull(retval.ipv6);
    Assert.assertNull(retval.serviceName);
    Assert.assertNull(retval.ipv6Bytes);
    Assert.assertNull(retval.ipv4);
  }

  // Test written by Diffblue Cover.
  @Test
  public void detectFamilyInputNotNullOutputNotNull()
    throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    // Arrange
    final String ipString = ",";
    // Act
    final Class<?> classUnderTest = Reflector.forName("zipkin2.Endpoint");
    final Method methodUnderTest =
      classUnderTest.getDeclaredMethod("detectFamily", Reflector.forName("java.lang.String"));
    methodUnderTest.setAccessible(true);
    final IpFamily retval = (IpFamily) methodUnderTest.invoke(null, ipString);
    // Assert result
    Assert.assertEquals(IpFamily.Unknown, retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void detectFamilyInputNotNullOutputNotNull2() {
    // Arrange
    final String ipString = ":.";
    // Act
    final Endpoint.IpFamily retval = Endpoint.detectFamily(ipString);
    // Assert result
    Assert.assertEquals(Endpoint.IpFamily.Unknown, retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void detectFamilyInputNotNullOutputNotNull3() {
    // Arrange
    final String ipString = "1";
    // Act
    final Endpoint.IpFamily retval = Endpoint.detectFamily(ipString);
    // Assert result
    Assert.assertEquals(Endpoint.IpFamily.Unknown, retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void detectFamilyInputNotNullOutputNotNull4() {
    // Arrange
    final String ipString = ".";
    // Act
    final Endpoint.IpFamily retval = Endpoint.detectFamily(ipString);
    // Assert result
    Assert.assertEquals(Endpoint.IpFamily.Unknown, retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void textToNumericFormatV6InputNotNullOutputNull()
    throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    // Arrange
    final String ipString = "700:0";
    // Act
    final Class<?> classUnderTest = Reflector.forName("zipkin2.Endpoint");
    final Method methodUnderTest = classUnderTest.getDeclaredMethod(
      "textToNumericFormatV6", Reflector.forName("java.lang.String"));
    methodUnderTest.setAccessible(true);
    final byte[] retval = (byte[]) methodUnderTest.invoke(null, ipString);
    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void textToNumericFormatV6InputNotNullOutputNull2()
    throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    // Arrange
    final String ipString = "7078:0:7";
    // Act
    final Class<?> classUnderTest = Reflector.forName("zipkin2.Endpoint");
    final Method methodUnderTest = classUnderTest.getDeclaredMethod(
      "textToNumericFormatV6", Reflector.forName("java.lang.String"));
    methodUnderTest.setAccessible(true);
    final byte[] retval = (byte[]) methodUnderTest.invoke(null, ipString);
    // Assert result
    Assert.assertNull(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void parseHextetInputNotNullOutputNumberFormatException() {
    // Arrange
    final String ipPart = "A1B2C3";
    // Act
    thrown.expect(NumberFormatException.class);
    Endpoint.parseHextet(ipPart);
    // Method is not expected to return due to exception thrown
  }

  // Test written by Diffblue Cover.
  @Test
  public void isValidIpV4WordInputNotNullZeroPositiveOutputFalse() {
    // Arrange
    final CharSequence word = "$\u0ffe";
    final int from = 0;
    final int toExclusive = 2;
    // Act
    final boolean retval = Endpoint.isValidIpV4Word(word, from, toExclusive);
    // Assert result
    Assert.assertFalse(retval);
  }

  // Test written by Diffblue Cover.
  @Test
  public void toStringOutputNotNull() throws InvocationTargetException {
    // Arrange
    final Endpoint objectUnderTest = (Endpoint) Reflector.getInstance("zipkin2.Endpoint");
    Reflector.setField(objectUnderTest, "port", -64);
    Reflector.setField(objectUnderTest, "ipv4Bytes", null);
    Reflector.setField(objectUnderTest, "ipv6", "3");
    Reflector.setField(objectUnderTest, "serviceName", "A1B2C3");
    Reflector.setField(objectUnderTest, "ipv6Bytes", null);
    Reflector.setField(objectUnderTest, "ipv4", "2");
    // Act
    final String retval = objectUnderTest.toString();
    // Assert result
    Assert.assertEquals("Endpoint{serviceName=A1B2C3, ipv4=2, ipv6=3, port=-64}", retval);
  }
}
