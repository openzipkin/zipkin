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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public final class Util {
  public static final Charset UTF_8 = Charset.forName("UTF-8");
  static final TimeZone UTC = TimeZone.getTimeZone("UTC");

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

  public static <T extends Comparable<? super T>> List<T> sortedList(@Nullable Collection<T> in) {
    if (in == null || in.isEmpty()) return Collections.emptyList();
    if (in.size() == 1) return Collections.singletonList(in.iterator().next());
    Object[] array = in.toArray();
    Arrays.sort(array);
    List result = Arrays.asList(array);
    return Collections.unmodifiableList(result);
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

  public static List<Date> getDays(long endTs, @Nullable Long lookback) {
    long to = midnightUTC(endTs);
    long from = midnightUTC(endTs - (lookback != null ? lookback : endTs));

    List<Date> days = new ArrayList<Date>();
    for (long time = from; time <= to; time += TimeUnit.DAYS.toMillis(1)) {
      days.add(new Date(time));
    }
    return days;
  }

  /** Parses a 1 to 16 character lower-hex string with no prefix int an unsigned long. */
  public static long lowerHexToUnsignedLong(String lowerHex) {
    char[] array = lowerHex.toCharArray();
    if (array.length < 1 || array.length > 16) {
      throw isntLowerHexLong(lowerHex);
    }
    long result = 0;
    for (char c : array) {
      result <<= 4;
      if (c >= '0' && c <= '9') {
        result |= c - '0';
      } else if (c >= 'a' && c <= 'f') {
        result |= c - 'a' + 10;
      } else {
        throw isntLowerHexLong(lowerHex);
      }
    }
    return result;
  }

  static NumberFormatException isntLowerHexLong(String lowerHex) {
    throw new NumberFormatException(
        lowerHex + " should be a 1 to 16 character lower-hex string with no prefix");
  }

  /** Inspired by {@code okio.Buffer.writeLong} */
  public static String toLowerHex(long v) {
    char[] data = new char[16];
    writeHexLong(data, 0, v);
    return new String(data);
  }

  /** Inspired by {@code okio.Buffer.writeLong} */
  public static void writeHexLong(char[] data, int pos, long v) {
    writeHexByte(data, pos + 0,  (byte) ((v >>> 56L) & 0xff));
    writeHexByte(data, pos + 2,  (byte) ((v >>> 48L) & 0xff));
    writeHexByte(data, pos + 4,  (byte) ((v >>> 40L) & 0xff));
    writeHexByte(data, pos + 6,  (byte) ((v >>> 32L) & 0xff));
    writeHexByte(data, pos + 8,  (byte) ((v >>> 24L) & 0xff));
    writeHexByte(data, pos + 10, (byte) ((v >>> 16L) & 0xff));
    writeHexByte(data, pos + 12, (byte) ((v >>> 8L) & 0xff));
    writeHexByte(data, pos + 14, (byte)  (v & 0xff));
  }

  static final char[] HEX_DIGITS =
      {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

  static void writeHexByte(char[] data, int pos, byte b) {
    data[pos + 0] = HEX_DIGITS[(b >> 4) & 0xf];
    data[pos + 1] = HEX_DIGITS[b & 0xf];
  }

  // throwable ctor not present in JRE 6
  static AssertionError assertionError(String message, Throwable cause) {
    AssertionError error = new AssertionError(message);
    error.initCause(cause);
    throw error;
  }

  private Util() {
  }
}
