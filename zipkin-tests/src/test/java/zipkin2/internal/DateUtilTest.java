/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.junit.Test;

import static java.util.concurrent.TimeUnit.DAYS;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.internal.DateUtil.midnightUTC;

public class DateUtilTest {

  @Test
  public void midnightUTCTest() throws ParseException {

    DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
    iso8601.setTimeZone(TimeZone.getTimeZone("UTC"));

    Date date = iso8601.parse("2011-04-15T20:08:18Z");

    long midnight = midnightUTC(date.getTime());

    assertThat(iso8601.format(new Date(midnight))).isEqualTo("2011-04-15T00:00:00Z");
  }

  @Test
  public void getDays() {
    assertThat(DateUtil.epochDays(DAYS.toMillis(2), DAYS.toMillis(1)))
        .containsExactly(DAYS.toMillis(1), DAYS.toMillis(2));
  }

  /** Looking back earlier than 1970 is likely a bug */
  @Test
  public void getDays_doesntLookEarlierThan1970() {
    assertThat(DateUtil.epochDays(DAYS.toMillis(2), DAYS.toMillis(3)))
        .containsExactly(0L, DAYS.toMillis(1), DAYS.toMillis(2));
  }
}
