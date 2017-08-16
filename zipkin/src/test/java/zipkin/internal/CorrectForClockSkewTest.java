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

import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Constants;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TestObjects;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static zipkin.Constants.CLIENT_RECV;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.LOCAL_COMPONENT;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.Constants.SERVER_SEND;
import static zipkin.TestObjects.APP_ENDPOINT;
import static zipkin.TestObjects.DB_ENDPOINT;
import static zipkin.TestObjects.WEB_ENDPOINT;
import static zipkin.internal.CorrectForClockSkew.getClockSkew;
import static zipkin.internal.CorrectForClockSkew.ipsMatch;
import static zipkin.internal.CorrectForClockSkew.isLocalSpan;

public class CorrectForClockSkewTest {
  List<String> messages = new ArrayList<>();

  Logger logger = new Logger("", null) {
    {
      setLevel(Level.ALL);
    }

    @Override public void log(Level level, String msg) {
      assertThat(level).isEqualTo(Level.FINE);
      messages.add(msg);
    }
  };

  static final long networkLatency = 10L;
  static final long now = System.currentTimeMillis();

  Endpoint both = TestObjects.WEB_ENDPOINT;
  Endpoint ipv6 = TestObjects.WEB_ENDPOINT.toBuilder().ipv4(0).build();
  Endpoint ipv4 = TestObjects.WEB_ENDPOINT.toBuilder().ipv6(null).build();

  @Test
  public void ipsMatch_falseWhenNoIp() {
    Endpoint noIp = Endpoint.builder().serviceName("foo").build();
    assertFalse(ipsMatch(noIp, ipv4));
    assertFalse(ipsMatch(noIp, ipv6));
    assertFalse(ipsMatch(ipv4, noIp));
    assertFalse(ipsMatch(ipv6, noIp));
  }

  /**
   * Instrumentation bugs might result in spans that look like clock skew is at play. When skew
   * appears on the same host, we assume it is an instrumentation bug (rather than make it worse
   * by adjusting it!)
   */
  @Test
  public void getClockSkew_mustBeOnDifferentHosts() {
    Span span = Span.builder()
        .traceId(1L).parentId(2L).id(3L).name("")
        .addAnnotation(Annotation.create(20, CLIENT_SEND, WEB_ENDPOINT))
        .addAnnotation(Annotation.create(10 /* skew */, SERVER_RECV, WEB_ENDPOINT))
        .addAnnotation(Annotation.create(20, SERVER_SEND, WEB_ENDPOINT))
        .addAnnotation(Annotation.create(40, CLIENT_RECV, WEB_ENDPOINT))
        .build();

    assertThat(getClockSkew(span)).isNull();
  }

  @Test
  public void getClockSkew_endpointIsServer() {
    Span span = Span.builder()
        .traceId(1L).parentId(2L).id(3L).name("")
        .addAnnotation(Annotation.create(20, CLIENT_SEND, WEB_ENDPOINT))
        .addAnnotation(Annotation.create(10 /* skew */, SERVER_RECV, APP_ENDPOINT))
        .addAnnotation(Annotation.create(20, SERVER_SEND, APP_ENDPOINT))
        .addAnnotation(Annotation.create(40, CLIENT_RECV, WEB_ENDPOINT))
        .build();

    assertThat(getClockSkew(span).endpoint).isEqualTo(APP_ENDPOINT);
  }

  /**
   * Skew is relative to the server receive and centered by the difference between the server
   * duration and the client duration.
   */
  @Test
  public void getClockSkew_includesSplitTheLatency() {
    Span span = Span.builder()
        .traceId(1L).parentId(2L).id(3L).name("")
        .addAnnotation(Annotation.create(20, CLIENT_SEND, WEB_ENDPOINT))
        .addAnnotation(Annotation.create(10 /* skew */, SERVER_RECV, APP_ENDPOINT))
        .addAnnotation(Annotation.create(20, SERVER_SEND, APP_ENDPOINT))
        .addAnnotation(Annotation.create(40, CLIENT_RECV, WEB_ENDPOINT))
        .build();

    assertThat(getClockSkew(span).skew).isEqualTo(-15);
  }

  /** We can't currently correct async spans, where the server lets go early. */
  @Test
  public void getClockSkew_onlyWhenClientDurationIsLongerThanServer() {
    Span span = Span.builder()
        .traceId(1L).parentId(2L).id(3L).name("")
        .addAnnotation(Annotation.create(20, CLIENT_SEND, WEB_ENDPOINT))
        .addAnnotation(Annotation.create(10 /* skew */, SERVER_RECV, APP_ENDPOINT))
        .addAnnotation(Annotation.create(20, SERVER_SEND, APP_ENDPOINT))
        .addAnnotation(Annotation.create(25, CLIENT_RECV, WEB_ENDPOINT))
        .build();

    assertThat(getClockSkew(span)).isNull();
  }

  @Test
  public void getClockSkew_basedOnServer() {
    Span span = Span.builder()
        .traceId(1L).parentId(2L).id(3L).name("")
        .addAnnotation(Annotation.create(20, CLIENT_SEND, WEB_ENDPOINT))
        .addAnnotation(Annotation.create(10 /* skew */, SERVER_RECV, APP_ENDPOINT))
        .addAnnotation(Annotation.create(20, SERVER_SEND, APP_ENDPOINT))
        .addAnnotation(Annotation.create(40, CLIENT_RECV, WEB_ENDPOINT))
        .build();

    assertThat(getClockSkew(span).endpoint).isEqualTo(APP_ENDPOINT);
  }

  @Test
  public void getClockSkew_requiresCoreAnnotationsToHaveEndpoints() {
    Span span = Span.builder()
        .traceId(1L).parentId(2L).id(3L).name("")
        .addAnnotation(Annotation.create(20, CLIENT_SEND, null))
        .addAnnotation(Annotation.create(10 /* skew */, SERVER_RECV, null))
        .addAnnotation(Annotation.create(20, SERVER_SEND, null))
        .addAnnotation(Annotation.create(40, CLIENT_RECV, null))
        .build();

    assertThat(getClockSkew(span)).isNull();
  }

  @Test
  public void ipsMatch_falseWhenIpv4Different() {
    Endpoint different = ipv4.toBuilder()
        .ipv4(1 << 24 | 2 << 16 | 3 << 8 | 4).build();
    assertFalse(ipsMatch(different, ipv4));
    assertFalse(ipsMatch(ipv4, different));
  }

  @Test
  public void ipsMatch_falseWhenIpv6Different() throws UnknownHostException {
    Endpoint different = ipv6.toBuilder()
        .ipv6(Inet6Address.getByName("2001:db8::c113").getAddress()).build();
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
    Span localSpan = localSpan(TestObjects.TRACE.get(0), WEB_ENDPOINT, 0, 0);
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
    Span rpcSpan = childSpan(rootSpan, APP_ENDPOINT, now + networkLatency, 1000L, skew);
    Span local = localSpan(rpcSpan, APP_ENDPOINT, rpcSpan.timestamp + 5, 200L);
    Span local2 = localSpan(local, APP_ENDPOINT, local.timestamp + 10, 100L);

    List<Span> adjustedSpans = CorrectForClockSkew.apply(asList(rpcSpan, rootSpan, local, local2));

    Span adjustedLocal = getById(adjustedSpans, local.id);
    assertThat(local.timestamp - skew)
        .isEqualTo(adjustedLocal.timestamp.longValue());

    Span adjustedLocal2 = getById(adjustedSpans, local2.id);
    assertThat(local2.timestamp - skew)
        .isEqualTo(adjustedLocal2.timestamp.longValue());
  }

  @Test
  public void skipsOnMissingRoot() {
    long networkLatency = 10L;
    Span rootSpan = createRootSpan(WEB_ENDPOINT, now, 2000L);
    long skew = -50000L;
    Span rpcSpan = childSpan(rootSpan, APP_ENDPOINT, now + networkLatency, 1000L, skew);
    List<Span> spans = asList(rootSpan.toBuilder().parentId(-1L).build(), rpcSpan);

    assertThat(CorrectForClockSkew.apply(logger, spans))
      .isSameAs(spans);
    assertThat(messages).containsExactly(
      "skipping clock skew adjustment due to missing root span: traceId=0000000000000001"
    );
  }
  @Test
  public void skipsOnDuplicateRoot() {
    long networkLatency = 10L;
    Span rootSpan = createRootSpan(WEB_ENDPOINT, now, 2000L);
    long skew = -50000L;
    Span rpcSpan = childSpan(rootSpan, APP_ENDPOINT, now + networkLatency, 1000L, skew);
    List<Span> spans = asList(rootSpan, rootSpan.toBuilder().id(-1).build(), rpcSpan);

    assertThat(CorrectForClockSkew.apply(logger, spans))
      .isSameAs(spans);
    assertThat(messages).containsExactly(
      "skipping redundant root span: traceId=0000000000000001, rootSpanId=0000000000000001, spanId=ffffffffffffffff",
      "skipping clock skew adjustment due to data errors: traceId=0000000000000001"
    );
  }

  @Test
  public void skipsOnCycle() {
    long networkLatency = 10L;
    Span rootSpan = createRootSpan(WEB_ENDPOINT, now, 2000L);
    long skew = -50000L;
    Span rpcSpan = childSpan(rootSpan, APP_ENDPOINT, now + networkLatency, 1000L, skew);
    List<Span> spans = asList(rootSpan, rpcSpan.toBuilder().parentId(rpcSpan.id).build());

    assertThat(CorrectForClockSkew.apply(logger, spans))
      .isSameAs(spans);
    assertThat(messages).containsExactly(
      "skipping circular dependency: traceId=0000000000000001, spanId=0000000000000002",
      "skipping clock skew adjustment due to data errors: traceId=0000000000000001"
    );
  }

  static void assertClockSkewIsCorrectlyApplied(long skew) {
    long rpcClientSendTs = now + 50L;
    long dbClientSendTimestamp = now + 60 + skew;

    long rootDuration = 350L;
    long rpcDuration = 250L;
    long dbDuration = 40L;

    Span rootSpan = createRootSpan(WEB_ENDPOINT, now, rootDuration);
    Span rpcSpan = childSpan(rootSpan, APP_ENDPOINT, rpcClientSendTs, rpcDuration, skew);
    Span tierSpan = childSpan(rpcSpan, DB_ENDPOINT, dbClientSendTimestamp, dbDuration, skew);

    List<Span> adjustedSpans = CorrectForClockSkew.apply(asList(rpcSpan, rootSpan, tierSpan));

    long id = rpcSpan.id;
    Span adjustedRpcSpan = getById(adjustedSpans, id);
    assertThat(annotationTimestamps(adjustedRpcSpan, Constants.SERVER_RECV))
        .containsExactly(rpcClientSendTs + networkLatency);

    assertThat(annotationTimestamps(adjustedRpcSpan, Constants.CLIENT_SEND))
        .containsExactly(adjustedRpcSpan.timestamp);

    Span adjustedTierSpan =
        getById(adjustedSpans, tierSpan.id);

    assertThat(annotationTimestamps(adjustedTierSpan, Constants.CLIENT_SEND))
        .containsExactly(adjustedTierSpan.timestamp);
  }

  static Span createRootSpan(Endpoint endPoint, long begin, long duration) {
    return Span.builder()
        .traceId(1L).id(1L).name("root").timestamp(begin)
        .addAnnotation(Annotation.create(begin, SERVER_RECV, endPoint))
        .addAnnotation(Annotation.create(begin + duration, SERVER_SEND, endPoint))
        .build();
  }

  static Span childSpan(Span parent, Endpoint to, long begin, long duration, long skew) {
    long spanId = parent.id + 1;
    Endpoint from = parent.annotations.get(0).endpoint;
    long networkLatency = 10L;
    return Span.builder()
        .traceId(parent.traceId).id(spanId).parentId(parent.id)
        .name("span" + spanId).timestamp(begin)
        .addAnnotation(Annotation.create(begin, CLIENT_SEND, from))
        .addAnnotation(Annotation.create(begin + skew + networkLatency, SERVER_RECV, to))
        .addAnnotation(Annotation.create(begin + skew + duration - networkLatency, SERVER_SEND, to))
        .addAnnotation(Annotation.create(begin + duration, CLIENT_RECV, from))
        .build();
  }

  static Span localSpan(Span parent, Endpoint endpoint, long begin, long duration) {
    long spanId = parent.id + 1;
    return Span.builder().traceId(parent.traceId).parentId(parent.id).id(spanId)
        .name("lc" + spanId)
        .timestamp(begin).duration(duration)
        .addBinaryAnnotation(BinaryAnnotation.create(LOCAL_COMPONENT, "lc" + spanId, endpoint))
        .build();
  }

  static Stream<Long> annotationTimestamps(Span span, String annotation) {
    return span.annotations.stream()
        .filter(a -> a.value.equals(annotation))
        .map(a -> a.timestamp);
  }

  static Span getById(List<Span> adjustedSpans, long id) {
    return adjustedSpans.stream().filter(s -> s.id == id).findFirst().get();
  }
}
