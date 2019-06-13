/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.internal;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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

  public static List<Date> getDays(long endTs, long lookback) {
    long to = midnightUTC(endTs);
    long startMillis = endTs - (lookback != 0 ? lookback : endTs);
    long from = startMillis <= 0 ? 0 : midnightUTC(startMillis); // >= 1970

    List<Date> days = new ArrayList<>();
    for (long time = from; time <= to; time += TimeUnit.DAYS.toMillis(1)) {
      days.add(new Date(time));
    }
    return days;
  }
}
