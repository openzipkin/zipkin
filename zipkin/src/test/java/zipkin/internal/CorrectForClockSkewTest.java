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
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TestObjects;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static zipkin.Constants.CLIENT_RECV;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.Constants.SERVER_SEND;
import static zipkin.TestObjects.APP_ENDPOINT;
import static zipkin.TestObjects.DB_ENDPOINT;
import static zipkin.TestObjects.WEB_ENDPOINT;
import static zipkin.internal.CorrectForClockSkew.ipsMatch;
import static zipkin.internal.CorrectForClockSkew.isLocalSpan;

public class CorrectForClockSkewTest {
  private static final long networkLatency = 10L;
  private static final long now = System.currentTimeMillis();

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
  public void spanWithSameEndPointIsLocalSpan() {
    assertTrue(isLocalSpan(TestObjects.TRACE.get(0)));
  }

  @Test
  public void spanWithLCAnnotationIsLocalSpan() {
    Span localSpan = createLocalSpan(TestObjects.TRACE.get(0), WEB_ENDPOINT, 0, 0);
    assertTrue(isLocalSpan(localSpan));
  }

  @Test
  public void clockSkewIsCorrectedIfRpcSpanSendsAfterClientReceive() {
    assertClockSkewIsCorrectlyApplied(50000);
  }

  @Test
  public void clockSkewIsCorrectedIfRpcSpanSendsBeforeClientSend() {
    assertClockSkewIsCorrectlyApplied(-50000);
  }

  @Test
  public void clockSkewIsPropagatedToLocalSpans() {
    long networkLatency = 10L;
    Span rootSpan = createRootSpan(WEB_ENDPOINT, now, 2000L);
    long skew = -50000L;
    Span rpcSpan = createChildSpan(rootSpan, WEB_ENDPOINT, APP_ENDPOINT, now + networkLatency, 1000L, skew);
    Span localSpan = createLocalSpan(rpcSpan, APP_ENDPOINT, rpcSpan.timestamp + 5, 200L);
    Span embeddedLocalSpan = createLocalSpan(localSpan, APP_ENDPOINT, localSpan.timestamp + 10, 100L);

    List<Span> adjustedSpans = CorrectForClockSkew.apply(Arrays.asList(rpcSpan, rootSpan, localSpan, embeddedLocalSpan));

    Span adjustedLocalSpan = adjustedSpans.stream().filter(s -> s.id == localSpan.id).findFirst().get();
    assertEquals(localSpan.timestamp - skew, adjustedLocalSpan.timestamp.longValue());

    Span adjustedEmbeddedLocalSpan = adjustedSpans.stream().filter(s -> s.id == embeddedLocalSpan.id).findFirst().get();
    assertEquals(embeddedLocalSpan.timestamp - skew, adjustedEmbeddedLocalSpan.timestamp.longValue());
  }

  private static void assertClockSkewIsCorrectlyApplied(long skew) {
    long rpcClientSendTs = now + 50L;
    long dbClientSendTimestamp = now + 60 + skew;

    long rootDuration = 350L;
    long rpcDuration = 250L;
    long dbDuration = 40L;

    Span rootSpan = createRootSpan(WEB_ENDPOINT, now, rootDuration);
    Span rpcSpan = createChildSpan(rootSpan, WEB_ENDPOINT, APP_ENDPOINT, rpcClientSendTs, rpcDuration, skew);
    Span tierSpan = createChildSpan(rpcSpan, APP_ENDPOINT, DB_ENDPOINT, dbClientSendTimestamp, dbDuration, skew);

    List<Span> adjustedSpans = CorrectForClockSkew.apply(Arrays.asList(rpcSpan, rootSpan, tierSpan));

    Span adjustedRpcSpan = adjustedSpans.stream().filter(s -> s.id == rpcSpan.id).findFirst().get();
    assertAnnotationTimestampEquals(rpcClientSendTs + networkLatency, adjustedRpcSpan, Constants.SERVER_RECV);
    assertAnnotationTimestampEquals(adjustedRpcSpan.timestamp, adjustedRpcSpan, Constants.CLIENT_SEND);

    Span adjustedTierSpan = adjustedSpans.stream().filter(s -> s.id == tierSpan.id).findFirst().get();
    assertAnnotationTimestampEquals(adjustedTierSpan.timestamp, adjustedTierSpan, Constants.CLIENT_SEND);
  }

  private static Span createRootSpan(Endpoint endPoint, long beginTs, long duration) {
    return Span.builder()
            .traceId(1L).id(1L).name("root").timestamp(beginTs)
            .addAnnotation(Annotation.create(beginTs, SERVER_RECV, endPoint))
            .addAnnotation(Annotation.create(beginTs + duration, SERVER_SEND, endPoint))
            .build();
  }
  private static Span createChildSpan(Span parentSpan, Endpoint from, Endpoint to, long beginTs, long duration, long skew) {
    long spanId = parentSpan.id + 1;
    long networkLatency = 10L;
    return Span.builder()
            .traceId(parentSpan.traceId).id(spanId).parentId(parentSpan.id).name("span" + spanId).timestamp(beginTs)
            .addAnnotation(Annotation.create(beginTs, CLIENT_SEND, from))
            .addAnnotation(Annotation.create(beginTs + skew + networkLatency, SERVER_RECV, to))
            .addAnnotation(Annotation.create(beginTs + skew + duration - networkLatency, SERVER_SEND, to))
            .addAnnotation(Annotation.create(beginTs + duration, CLIENT_RECV, from))
            .build();
  }

  private static Span createLocalSpan(Span parentSpan, Endpoint endPoint, long beginTs, long duration) {
    long spanId = parentSpan.id + 1;
    return Span.builder().traceId(parentSpan.traceId).id(spanId).parentId(parentSpan.id).name("localcomponent" + spanId)
            .timestamp(beginTs).duration(duration)
            .addBinaryAnnotation(BinaryAnnotation.create(Constants.LOCAL_COMPONENT, "localComponent" + spanId, endPoint))
            .build();
  }

  private static void assertAnnotationTimestampEquals(long expectedTimestamp, Span span, String annotation) {
    assertThat(span.annotations)
            .filteredOn(a -> a.value.equals(annotation))
            .extracting(a -> a.timestamp)
            .first()
            .isEqualTo(expectedTimestamp);
  }
}
