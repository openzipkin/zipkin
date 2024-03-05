/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public final class DateUtil {
  static final TimeZone UTC = TimeZone.getTimeZone("UTC");

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

  public static List<Long> epochDays(long endTs, long lookback) {
    long to = midnightUTC(endTs);
    long startMillis = endTs - (lookback != 0 ? lookback : endTs);
    long from = startMillis <= 0 ? 0 : midnightUTC(startMillis); // >= 1970

    List<Long> days = new ArrayList<>();
    for (long time = from; time <= to; time += TimeUnit.DAYS.toMillis(1)) {
      days.add(time);
    }
    return days;
  }
}
