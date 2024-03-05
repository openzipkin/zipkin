/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.internal.DateUtil.midnightUTC;

class DateUtilTest {

  @Test void midnightUTCTest() throws ParseException {

    DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
    iso8601.setTimeZone(TimeZone.getTimeZone("UTC"));

    Date date = iso8601.parse("2011-04-15T20:08:18Z");

    long midnight = midnightUTC(date.getTime());

    assertThat(iso8601.format(new Date(midnight))).isEqualTo("2011-04-15T00:00:00Z");
  }

  @Test void getDays() {
    assertThat(DateUtil.epochDays(DAYS.toMillis(2), DAYS.toMillis(1)))
        .containsExactly(DAYS.toMillis(1), DAYS.toMillis(2));
  }

  /** Looking back earlier than 1970 is likely a bug */
  @Test void getDays_doesntLookEarlierThan1970() {
    assertThat(DateUtil.epochDays(DAYS.toMillis(2), DAYS.toMillis(3)))
        .containsExactly(0L, DAYS.toMillis(1), DAYS.toMillis(2));
  }
}
