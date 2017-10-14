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
package zipkin2.elasticsearch.internal;

import com.google.auto.value.AutoValue;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import zipkin2.internal.Nullable;

@AutoValue
public abstract class IndexNameFormatter {
  public static Builder newBuilder() {
    return new AutoValue_IndexNameFormatter.Builder();
  }

  public abstract Builder toBuilder();

  private static final String DAILY_INDEX_FORMAT = "yyyy-MM-dd";
  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  public abstract String index();

  abstract char dateSeparator();

  abstract ThreadLocal<SimpleDateFormat> dateFormat(); // SimpleDateFormat isn't thread-safe

  @AutoValue.Builder public static abstract class Builder {
    public abstract Builder index(String index);

    public abstract Builder dateSeparator(char dateSeparator);

    abstract Builder dateFormat(ThreadLocal<SimpleDateFormat> dateFormat);

    abstract char dateSeparator();

    public final IndexNameFormatter build() {
      return dateFormat(new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() {
          SimpleDateFormat result =
            new SimpleDateFormat(DAILY_INDEX_FORMAT.replace('-', dateSeparator()));
          result.setTimeZone(TimeZone.getTimeZone("UTC"));
          return result;
        }
      }).autoBuild();
    }

    abstract IndexNameFormatter autoBuild();
  }

  /**
   * Returns a set of index patterns that represent the range provided. Notably, this compresses
   * months or years using wildcards (in order to send smaller API calls).
   *
   * <p>For example, if {@code beginMillis} is 2016-11-30 and {@code endMillis} is 2017-01-02, the
   * result will be 2016-11-30, 2016-12-*, 2017-01-01 and 2017-01-02.
   */
  public List<String> formatTypeAndRange(@Nullable String type, long beginMillis, long endMillis) {
    GregorianCalendar current = midnightUTC(beginMillis);
    GregorianCalendar end = midnightUTC(endMillis);
    if (current.equals(end)) {
      return Collections.singletonList(formatTypeAndTimestamp(type, current.getTimeInMillis()));
    }

    String prefix = prefix(type);
    List<String> indices = new ArrayList<>();
    while (current.compareTo(end) <= 0) {
      if (current.get(Calendar.MONTH) == 0 && current.get(Calendar.DATE) == 1) {
        // attempt to compress a year
        current.set(Calendar.DAY_OF_YEAR, current.getActualMaximum(Calendar.DAY_OF_YEAR));
        if (current.compareTo(end) <= 0) {
          indices.add(
            String.format("%s-%s%c*", prefix, current.get(Calendar.YEAR), dateSeparator()));
          current.add(Calendar.DATE, 1); // rollover to next year
          continue;
        } else {
          current.set(Calendar.DAY_OF_YEAR, 1); // rollback to first of the year
        }
      } else if (current.get(Calendar.DATE) == 1) {
        // attempt to compress a month
        current.set(Calendar.DATE, current.getActualMaximum(Calendar.DATE));
        if (current.compareTo(end) <= 0) {
          indices.add(String.format("%s-%s%c%02d%c*", prefix,
            current.get(Calendar.YEAR), dateSeparator(),
            current.get(Calendar.MONTH) + 1, dateSeparator()
          ));
          current.add(Calendar.DATE, 1); // rollover to next month
          continue;
        } else {
          current.set(Calendar.DATE, 1); // rollback to first of the month
        }
      }
      indices.add(formatTypeAndTimestamp(type, current.getTimeInMillis()));
      current.add(Calendar.DATE, 1);
    }
    return indices;
  }

  static GregorianCalendar midnightUTC(long epochMillis) {
    GregorianCalendar result = new GregorianCalendar(UTC);
    Calendar day = Calendar.getInstance(UTC);
    day.setTimeInMillis(epochMillis);
    day.set(Calendar.MILLISECOND, 0);
    day.set(Calendar.SECOND, 0);
    day.set(Calendar.MINUTE, 0);
    day.set(Calendar.HOUR_OF_DAY, 0);
    result.setTimeInMillis(day.getTimeInMillis());
    return result;
  }

  public String formatTypeAndTimestamp(@Nullable String type, long timestampMillis) {
    return prefix(type) + "-" + dateFormat().get().format(new Date(timestampMillis));
  }

  private String prefix(@Nullable String type) {
    return type != null ? index() + ":" + type : index();
  }

  // for testing
  public long parseDate(String timestamp) {
    try {
      return dateFormat().get().parse(timestamp).getTime();
    } catch (ParseException e) {
      throw new AssertionError(e);
    }
  }

  public String formatType(@Nullable String type) {
    return prefix(type) + "-*";
  }
}
