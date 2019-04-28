/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2;

import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;

public final class TestObjects {
  public static final Charset UTF_8 = Charset.forName("UTF-8");
  /** Notably, the cassandra implementation has day granularity */
  public static final long DAY = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);

  static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  // Use real time, as most span-stores have TTL logic which looks back several days.
  public static final long TODAY = midnightUTC(System.currentTimeMillis());

  public static final Endpoint FRONTEND =
      Endpoint.newBuilder().serviceName("frontend").ip("127.0.0.1").build();
  public static final Endpoint BACKEND =
      Endpoint.newBuilder().serviceName("backend").ip("192.168.99.101").port(9000).build();
  public static final Endpoint DB =
      Endpoint.newBuilder().serviceName("db").ip("2001:db8::c001").port(3036).build();

  /** For bucketed data floored to the day. For example, dependency links. */
  public static long midnightUTC(long epochMillis) {
    Calendar day = Calendar.getInstance(UTC);
    day.setTimeInMillis(epochMillis);
    day.set(Calendar.MILLISECOND, 0);
    day.set(Calendar.SECOND, 0);
    day.set(Calendar.MINUTE, 0);
    day.set(Calendar.HOUR_OF_DAY, 0);
    return day.getTimeInMillis();
  }

  public static final Span CLIENT_SPAN =
    Span.newBuilder()
      .traceId("7180c278b62e8f6a216a2aea45d08fc9")
      .parentId("1")
      .id("2")
      .name("get")
      .kind(Span.Kind.CLIENT)
      .localEndpoint(FRONTEND)
      .remoteEndpoint(BACKEND)
      .timestamp((TODAY + 50L) * 1000L)
      .duration(200 * 1000L)
      .addAnnotation((TODAY + 100) * 1000L, "foo")
      .putTag("http.path", "/api")
      .putTag("clnt/finagle.version", "6.45.0")
      .build();
  public static final List<Span> TRACE = asList(
    Span.newBuilder().traceId(CLIENT_SPAN.traceId()).id("1")
      .name("get")
      .kind(Span.Kind.SERVER)
      .localEndpoint(FRONTEND)
      .timestamp(TODAY * 1000L)
      .duration(350 * 1000L)
      .build(),
    CLIENT_SPAN,
    Span.newBuilder().traceId(CLIENT_SPAN.traceId())
      .parentId(CLIENT_SPAN.parentId()).id(CLIENT_SPAN.id()).shared(true)
      .name("get")
      .kind(Span.Kind.SERVER)
      .localEndpoint(BACKEND)
      .timestamp((TODAY + 100L) * 1000L)
      .duration(150 * 1000L)
      .build(),
    Span.newBuilder()
      .traceId(CLIENT_SPAN.traceId())
      .parentId("2")
      .id("3")
      .name("query")
      .kind(Span.Kind.CLIENT)
      .localEndpoint(BACKEND)
      .remoteEndpoint(DB)
      .timestamp((TODAY + 150L) * 1000L)
      .duration(50 * 1000L)
      .addAnnotation((TODAY + 190) * 1000L, "⻩")
      .putTag("error", "\uD83D\uDCA9")
      .build()
  );
  // storage query units are milliseconds, while trace data is microseconds
  public static final long TRACE_DURATION = TRACE.get(0).durationAsLong() / 1000;
  public static final long TRACE_STARTTS = TRACE.get(0).timestampAsLong() / 1000;
  public static final long TRACE_ENDTS = TRACE_STARTTS + TRACE_DURATION;

  static final Span.Builder spanBuilder = spanBuilder();

  /** Reuse a builder as it is significantly slows tests to create 100000 of these! */
  static Span.Builder spanBuilder() {
    return Span.newBuilder()
        .name("get /foo")
        .timestamp(System.currentTimeMillis() * 1000)
        .duration(1000)
        .kind(Span.Kind.SERVER)
        .localEndpoint(BACKEND)
        .putTag("http.method", "GET");
  }

  /**
   * Zipkin trace ids are random 64bit numbers. This creates a relatively large input to avoid
   * flaking out due to PRNG nuance.
   */
  public static final Span[] LOTS_OF_SPANS =
      new Random().longs(100_000).mapToObj(t -> span(t)).toArray(Span[]::new);

  public static Span span(long traceId) {
    return spanBuilder.traceId(Long.toHexString(traceId)).id(traceId).build();
  }
}
