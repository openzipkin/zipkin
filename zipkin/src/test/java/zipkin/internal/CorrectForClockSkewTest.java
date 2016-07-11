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
package zipkin.internal;

import org.junit.Test;
import zipkin.Endpoint;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static zipkin.internal.CorrectForClockSkew.ipsMatch;

public class CorrectForClockSkewTest {
  Endpoint ipv6 = Endpoint.builder()
      .serviceName("web")
      // Cheat so we don't have to catch an exception here
      .ipv6(sun.net.util.IPAddressUtil.textToNumericFormatV6("2001:db8::c001"))
      .build();

  Endpoint ipv4 = Endpoint.builder()
      .serviceName("web")
      .ipv4(124 << 24 | 13 << 16 | 90 << 8 | 2)
      .build();

  Endpoint both = ipv4.toBuilder().ipv6(ipv6.ipv6).build();

  @Test
  public void ipsMatch_falseWhenNoIp() {
    Endpoint noIp = Endpoint.builder().serviceName("foo").build();
    assertFalse(ipsMatch(noIp, ipv4));
    assertFalse(ipsMatch(noIp, ipv6));
    assertFalse(ipsMatch(ipv4, noIp));
    assertFalse(ipsMatch(ipv6, noIp));
  }

  @Test
  public void ipsMatch_falseWhenIpv4Different() {
    Endpoint different = ipv4.toBuilder()
        .ipv4(124 << 24 | 13 << 16 | 90 << 8 | 3).build();
    assertFalse(ipsMatch(different, ipv4));
    assertFalse(ipsMatch(ipv4, different));
  }

  @Test
  public void ipsMatch_falseWhenIpv6Different() {
    Endpoint different = ipv6.toBuilder()
        .ipv6(sun.net.util.IPAddressUtil.textToNumericFormatV6("2001:db8::c002")).build();
    assertFalse(ipsMatch(different, ipv6));
    assertFalse(ipsMatch(ipv6, different));
  }

  @Test
  public void ipsMatch_whenIpv6Match() {
    assertTrue(ipsMatch(ipv6, ipv6));
    assertTrue(ipsMatch(both, ipv6));
    assertTrue(ipsMatch(ipv6, both));
  }

  @Test
  public void ipsMatch_whenIpv4Match() {
    assertTrue(ipsMatch(ipv4, ipv4));
    assertTrue(ipsMatch(both, ipv4));
    assertTrue(ipsMatch(ipv4, both));
  }
}
