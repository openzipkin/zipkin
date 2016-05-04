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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

public final class Util {
  public static final Charset UTF_8 = Charset.forName("UTF-8");
  static final TimeZone UTC = TimeZone.getTimeZone("UTC");
  static final Pattern HEX_ID_PATTERN = Pattern.compile("^[0-9a-f]{1,16}$");

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

  /** Parses a 1 to 16 character lower-hex string with no prefix int an unsigned long. */
  public static long lowerHexToUnsignedLong(String lowerHex) {
    if (!HEX_ID_PATTERN.matcher(lowerHex).matches()) {
      throw new NumberFormatException(
          lowerHex + " should be a 1 to 16 character lower-hex string with no prefix");
    }

    long result = 0;
    for (char c : lowerHex.toCharArray()) {
      result <<= 4;
      result |= c <= '9' ? c - '0' : c - 'a' + 10;
    }
    return result;
  }

  private Util() {
  }
}
