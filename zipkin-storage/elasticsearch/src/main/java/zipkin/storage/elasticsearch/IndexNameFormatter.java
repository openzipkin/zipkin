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
package zipkin.storage.elasticsearch;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TimeZone;
import zipkin.internal.Util;

final class IndexNameFormatter {
  private static final String DAILY_INDEX_FORMAT = "yyyy-MM-dd";
  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  private final String index;
  // SimpleDateFormat isn't thread-safe
  private final ThreadLocal<SimpleDateFormat> dateFormat;

  IndexNameFormatter(String index) {
    this.index = index;
    this.dateFormat = new ThreadLocal<SimpleDateFormat>() {
      @Override protected SimpleDateFormat initialValue() {
        SimpleDateFormat result = new SimpleDateFormat(DAILY_INDEX_FORMAT);
        result.setTimeZone(TimeZone.getTimeZone("UTC"));
        return result;
      }
    };
  }

  /**
   * Returns a set of index patterns that represent the range provided. Notably, this compresses
   * months or years using wildcards (in order to send smaller API calls).
   *
   * <p>For example, if {@code beginMillis} is 2016-11-30 and {@code endMillis} is 2017-01-02, the
   * result will be 2016-11-30, 2016-12-*, 2017-01-01 and 2017-01-02.
   */
  Set<String> indexNamePatternsForRange(long beginMillis, long endMillis) {
    GregorianCalendar current = midnightUTC(beginMillis);
    GregorianCalendar end = midnightUTC(endMillis);
    if (current.equals(end)) {
      return Collections.singleton(indexNameForTimestamp(current.getTimeInMillis()));
    }

    Set<String> indices = new LinkedHashSet<>();
    while (current.compareTo(end) <= 0) {
      if (current.get(Calendar.MONTH) == 0 && current.get(Calendar.DATE) == 1) {
        // attempt to compress a year
        current.set(Calendar.DAY_OF_YEAR, current.getActualMaximum(Calendar.DAY_OF_YEAR));
        if (current.compareTo(end) <= 0) {
          indices.add(String.format("%s-%s-*", index, current.get(Calendar.YEAR)));
          current.add(Calendar.DATE, 1); // rollover to next year
          continue;
        } else {
          current.set(Calendar.DAY_OF_YEAR, 1); // rollback to first of the year
        }
      } else if (current.get(Calendar.DATE) == 1) {
        // attempt to compress a month
        current.set(Calendar.DATE, current.getActualMaximum(Calendar.DATE));
        if (current.compareTo(end) <= 0) {
          indices.add(String.format("%s-%s-%02d-*", index,
              current.get(Calendar.YEAR),
              current.get(Calendar.MONTH) + 1
          ));
          current.add(Calendar.DATE, 1); // rollover to next month
          continue;
        } else {
          current.set(Calendar.DATE, 1); // rollback to first of the month
        }
      }
      indices.add(indexNameForTimestamp(current.getTimeInMillis()));
      current.add(Calendar.DATE, 1);
    }
    return indices;
  }

  static GregorianCalendar midnightUTC(long epochMillis) {
    GregorianCalendar result = new GregorianCalendar(UTC);
    result.setTimeInMillis(Util.midnightUTC(epochMillis));
    return result;
  }

  String indexNameForTimestamp(long timestampMillis) {
    return index + "-" + dateFormat.get().format(new Date(timestampMillis));
  }

  String catchAll() {
    return index + "-*";
  }
}
