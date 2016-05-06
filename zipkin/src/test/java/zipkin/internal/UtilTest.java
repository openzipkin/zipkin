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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static zipkin.internal.Util.equal;
import static zipkin.internal.Util.lowerHexToUnsignedLong;
import static zipkin.internal.Util.midnightUTC;
import static zipkin.internal.Util.toLowerHex;

public class UtilTest {
  @Test
  public void equalTest() {
    assertTrue(equal(null, null));
    assertTrue(equal("1", "1"));
    assertFalse(equal(null, "1"));
    assertFalse(equal("1", null));
    assertFalse(equal("1", "2"));
  }

  @Test
  public void midnightUTCTest() throws ParseException {

    DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
    iso8601.setTimeZone(TimeZone.getTimeZone("UTC"));

    Date date = iso8601.parse("2011-04-15T20:08:18Z");

    long midnight = midnightUTC(date.getTime());

    assertThat(iso8601.format(new Date(midnight)))
        .isEqualTo("2011-04-15T00:00:00Z");
  }

  @Test
  public void lowerHexToUnsignedLongTest() {
    assertThat(lowerHexToUnsignedLong("ffffffffffffffff")).isEqualTo(-1);
    assertThat(lowerHexToUnsignedLong("0")).isEqualTo(0);
    assertThat(lowerHexToUnsignedLong(Long.toHexString(Long.MAX_VALUE))).isEqualTo(Long.MAX_VALUE);

    try {
      lowerHexToUnsignedLong("fffffffffffffffff"); // too long
      failBecauseExceptionWasNotThrown(NumberFormatException.class);
    } catch (NumberFormatException e) {

    }

    try {
      lowerHexToUnsignedLong(""); // too short
      failBecauseExceptionWasNotThrown(NumberFormatException.class);
    } catch (NumberFormatException e) {

    }

    try {
      lowerHexToUnsignedLong("rs"); // bad charset
      failBecauseExceptionWasNotThrown(NumberFormatException.class);
    } catch (NumberFormatException e) {

    }
  }

  @Test
  public void toLowerHex_minValue() {
    assertThat(toLowerHex(Long.MAX_VALUE)).isEqualTo("7fffffffffffffff");
  }

  @Test
  public void toLowerHex_midValue() {
    assertThat(toLowerHex(3405691582L)).isEqualTo("00000000cafebabe");
  }

  @Test
  public void toLowerHex_fixedLength() {
    assertThat(toLowerHex(0L)).isEqualTo("0000000000000000");
  }
}
