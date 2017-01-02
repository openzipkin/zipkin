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
package zipkin.internal;

import org.junit.Test;
import zipkin.Annotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Span;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static zipkin.Constants.CLIENT_RECV;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.Constants.SERVER_SEND;
import static zipkin.TestObjects.APP_ENDPOINT;
import static zipkin.TestObjects.WEB_ENDPOINT;
import static zipkin.internal.CorrectForClockSkew.asMap;
import static zipkin.internal.CorrectForClockSkew.getTimestamp;
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

  @Test
  public void clockSkewIsCorrectedIfRpcSpanSendsAfterClientReceive() {
    long now = System.currentTimeMillis();

    final long traceId = 1L;
    final long rootSpanId = 1L;
    final long rpcSpanId = 2L;
    Span rootSpan = Span.builder()
            .traceId(traceId).id(rootSpanId).name("root").timestamp(now * 1000)
            .addAnnotation(Annotation.create(now * 1000, SERVER_RECV, WEB_ENDPOINT))
            .addAnnotation(Annotation.create((now + 350) * 1000, SERVER_SEND, WEB_ENDPOINT))
            .build();
    long skew = 50000;
    long clientSendTimestamp = (now + 50) * 1000;
    Span rpcSpan = Span.builder()
            .traceId(traceId).id(rpcSpanId).parentId(rootSpanId).name("rpc")
            .addAnnotation(Annotation.create(clientSendTimestamp, CLIENT_SEND, WEB_ENDPOINT))
            .addAnnotation(Annotation.create((now + 50 + skew) * 1000, SERVER_RECV, APP_ENDPOINT))
            .addAnnotation(Annotation.create((now + 150 + skew) * 1000, SERVER_SEND, APP_ENDPOINT))
            .addAnnotation(Annotation.create((now + 300) * 1000, CLIENT_RECV, WEB_ENDPOINT))
            .build();
    List<Span> adjustedSpans = CorrectForClockSkew.apply(Arrays.asList(rpcSpan, rootSpan));

    Span adjustedRpcSpan = adjustedSpans.stream().filter(s -> s.id == rpcSpanId).findFirst().get();
    long adjustedRpcSpanSRTimestamp = getTimestamp(asMap(adjustedRpcSpan.annotations), Constants.SERVER_RECV);

    long serverDuration = getSpanDuration(rpcSpan, Constants.SERVER_RECV, Constants.SERVER_SEND);
    long clientDuration = getSpanDuration(rpcSpan, Constants.CLIENT_SEND, Constants.CLIENT_RECV);
    long expectedSkew = (clientDuration - serverDuration) / 2L;
    assertEquals(clientSendTimestamp + expectedSkew, adjustedRpcSpanSRTimestamp);
  }

  private long getSpanDuration(Span span, String beginAnnotation, String endAnnotation) {
    Map<String, Annotation> annotationsAsMap = asMap(span.annotations);
    return getTimestamp(annotationsAsMap, endAnnotation) - getTimestamp(annotationsAsMap, beginAnnotation);
  }
}
