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

import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.internal.Dependencies;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static zipkin.Constants.CLIENT_ADDR;
import static zipkin.Constants.CLIENT_RECV;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.Constants.ERROR;
import static zipkin.Constants.SERVER_ADDR;
import static zipkin.Constants.SERVER_RECV;
import static zipkin.Constants.SERVER_SEND;
import static zipkin.internal.Util.midnightUTC;

public final class TestObjects {

  /** Notably, the cassandra implementation has day granularity */
  public static final long DAY = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);

  // Use real time, as most span-stores have TTL logic which looks back several days.
  public static final long TODAY = midnightUTC(System.currentTimeMillis());

  public static final Endpoint WEB_ENDPOINT = Endpoint.builder()
      .serviceName("web")
      .ipv4(124 << 24 | 13 << 16 | 90 << 8 | 3)
      .ipv6(fromIPv6Literal("2001:db8::c001"))
      .port(80).build();
  public static final Endpoint APP_ENDPOINT =
      Endpoint.builder().serviceName("app").ipv4(172 << 24 | 17 << 16 | 2).port(8080).build();
  public static final Endpoint DB_ENDPOINT =
      Endpoint.builder().serviceName("db").ipv4(172 << 24 | 17 << 16 | 2).port(3306).build();
  public static final Endpoint NO_IP_ENDPOINT = Endpoint.builder().serviceName("no_ip").build();

  static final long WEB_SPAN_ID = -692101025335252320L;
  static final long APP_SPAN_ID = -7842865617155193778L;
  static final long DB_SPAN_ID = 8207293009014896295L;

  public static final List<Span> TRACE = asList(
      Span.builder().traceId(WEB_SPAN_ID).id(WEB_SPAN_ID).name("get")
          .addAnnotation(Annotation.create(TODAY * 1000, SERVER_RECV, WEB_ENDPOINT))
          .addAnnotation(Annotation.create((TODAY + 350) * 1000, SERVER_SEND, WEB_ENDPOINT))
          .build(),
      Span.builder().traceId(WEB_SPAN_ID).parentId(WEB_SPAN_ID).id(APP_SPAN_ID).name("get")
          .addAnnotation(Annotation.create((TODAY + 50) * 1000, CLIENT_SEND, WEB_ENDPOINT))
          .addAnnotation(Annotation.create((TODAY + 100) * 1000, SERVER_RECV, APP_ENDPOINT))
          .addAnnotation(Annotation.create((TODAY + 250) * 1000, SERVER_SEND, APP_ENDPOINT))
          .addAnnotation(Annotation.create((TODAY + 300) * 1000, CLIENT_RECV, WEB_ENDPOINT))
          .addBinaryAnnotation(BinaryAnnotation.address(CLIENT_ADDR, WEB_ENDPOINT))
          .addBinaryAnnotation(BinaryAnnotation.address(SERVER_ADDR, APP_ENDPOINT))
          .build(),
      Span.builder().traceId(WEB_SPAN_ID).parentId(APP_SPAN_ID).id(DB_SPAN_ID).name("query")
          .addAnnotation(Annotation.create((TODAY + 150) * 1000, CLIENT_SEND, APP_ENDPOINT))
          .addAnnotation(Annotation.create((TODAY + 200) * 1000, CLIENT_RECV, APP_ENDPOINT))
          .addAnnotation(Annotation.create((TODAY + 190) * 1000, "⻩", NO_IP_ENDPOINT))
          .addBinaryAnnotation(BinaryAnnotation.address(SERVER_ADDR, DB_ENDPOINT))
          .addBinaryAnnotation(BinaryAnnotation.create(ERROR, "\uD83D\uDCA9", NO_IP_ENDPOINT))
          .build()
  ).stream().map(ApplyTimestampAndDuration::apply).collect(toList());

  public static final List<DependencyLink> LINKS = asList(
    DependencyLink.builder().parent("web").child("app").callCount(1L).build(),
    DependencyLink.builder().parent("app").child("db").callCount(1L).errorCount(1L).build()
  );
  public static final Dependencies DEPENDENCIES = Dependencies.create(TODAY, TODAY + 1000, LINKS);

  static final Span.Builder spanBuilder = spanBuilder();

  /** Reuse a builder as it is significantly slows tests to create 100000 of these! */
  static Span.Builder spanBuilder() {
    Endpoint e = Endpoint.builder().serviceName("service").ipv4(127 << 24 | 1).port(8080).build();
    Annotation sr = Annotation.create(System.currentTimeMillis() * 1000, SERVER_RECV, e);
    Annotation ss = Annotation.create(sr.timestamp + 1000, SERVER_SEND, e);
    BinaryAnnotation ba = BinaryAnnotation.create(TraceKeys.HTTP_METHOD, "GET", e);
    return Span.builder().name("get").addAnnotation(sr).addAnnotation(ss).addBinaryAnnotation(ba);
  }

  /**
   * Zipkin trace ids are random 64bit numbers. This creates a relatively large input to avoid
   * flaking out due to PRNG nuance.
   */
  public static final Span[] LOTS_OF_SPANS =
      new Random().longs(100_000).mapToObj(t -> span(t)).toArray(Span[]::new);

  public static Span span(long traceId) {
    return spanBuilder.traceId(traceId).id(traceId).build();
  }

  static byte[] fromIPv6Literal(String literal) {
    try {
      return Inet6Address.getByName(literal).getAddress();
    } catch (UnknownHostException e) {
      throw new AssertionError(e);
    }
  }
}
