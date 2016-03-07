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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import okio.Buffer;
import okio.GzipSink;
import okio.GzipSource;

public final class Util {
  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
  public static final Charset UTF_8 = Charset.forName("UTF-8");

  public static int envOr(String key, int fallback) {
    return System.getenv(key) != null ? Integer.parseInt(System.getenv(key)) : fallback;
  }

  public static String envOr(String key, String fallback) {
    return System.getenv(key) != null ? System.getenv(key) : fallback;
  }

  public static boolean equal(Object a, Object b) {
    return a == b || (a != null && a.equals(b));
  }

  /**
   * Copy of {@code com.google.common.base.Preconditions#checkArgument}.
   */
  public static void checkArgument(boolean expression,
                                   String errorMessageTemplate,
                                   Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalArgumentException(String.format(errorMessageTemplate, errorMessageArgs));
    }
  }

  /**
   * Copy of {@code com.google.common.base.Preconditions#checkNotNull}.
   */
  public static <T> T checkNotNull(T reference, String errorMessage) {
    if (reference == null) {
      // If either of these parameters is null, the right thing happens anyway
      throw new NullPointerException(errorMessage);
    }
    return reference;
  }

  public static <T extends Comparable<? super T>> List<T> sortedList(@Nullable Collection<T> input) {
    if (input == null || input.isEmpty()) return Collections.emptyList();
    if (input.size() == 1) return Collections.singletonList(input.iterator().next());
    List<T> result = new ArrayList<>(input);
    Collections.sort(result);
    return Collections.unmodifiableList(result);
  }

  public static byte[] gzip(byte[] bytes) throws IOException {
    Buffer sink = new Buffer();
    GzipSink gzipSink = new GzipSink(sink);
    gzipSink.write(new Buffer().write(bytes), bytes.length);
    gzipSink.close();
    return sink.readByteArray();
  }

  public static byte[] gunzip(byte[] bytes) throws IOException {
    Buffer result = new Buffer();
    GzipSource source = new GzipSource(new Buffer().write(bytes));
    while (source.read(result, Integer.MAX_VALUE) != -1) ;
    return result.readByteArray();
  }

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

  private Util() {
  }
}
