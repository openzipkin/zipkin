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

import java.nio.charset.Charset;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public final class TestObjects {
  public static final Charset UTF_8 = Charset.forName("UTF-8");
  /** Notably, the cassandra implementation has day granularity */
  public static final long DAY = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);
  public static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  // Use real time, as most span-stores have TTL logic which looks back several days.
  public static final long TODAY = midnightUTC(System.currentTimeMillis());

  public static final Endpoint FRONTEND = Endpoint.newBuilder()
    .serviceName("frontend")
    .ip("127.0.0.1")
    .build();
  public static final Endpoint BACKEND = Endpoint.newBuilder()
    .serviceName("backend")
    .ip("192.168.99.101")
    .port(9000)
    .build();

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

  public static final Span CLIENT_SPAN = Span.newBuilder()
    .traceId("7180c278b62e8f6a216a2aea45d08fc9")
    .parentId("6b221d5bc9e6496c")
    .id("5b4185666d50f68b")
    .name("get")
    .kind(Span.Kind.CLIENT)
    .localEndpoint(FRONTEND)
    .remoteEndpoint(BACKEND)
    .timestamp((TODAY - 207) * 1000L)
    .duration(207 * 1000L)
    .addAnnotation((TODAY - 100) * 1000L, "foo")
    .putTag("http.path", "/api")
    .putTag("clnt/finagle.version", "6.45.0")
    .build();
}
