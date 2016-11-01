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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IndexNameFormatterTest {
  IndexNameFormatter formatter = new IndexNameFormatter("zipkin");
  DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

  public IndexNameFormatterTest() {
    iso8601.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  @Test
  public void indexNameForTimestampRange_sameDay() throws ParseException {
    long start = iso8601.parse("2016-11-01T01:01:01Z").getTime();
    long end = iso8601.parse("2016-11-01T23:59:59Z").getTime();

    assertThat(formatter.indexNamePatternsForRange(start, end))
        .containsExactly("zipkin-2016-11-01");
  }

  @Test
  public void indexNameForTimestampRange_sameMonth() throws ParseException {
    long start = iso8601.parse("2016-11-15T01:01:01Z").getTime();
    long end = iso8601.parse("2016-11-16T23:59:59Z").getTime();

    assertThat(formatter.indexNamePatternsForRange(start, end))
        .containsExactly("zipkin-2016-11-15", "zipkin-2016-11-16");
  }

  @Test
  public void indexNameForTimestampRange_sameMonth_startingAtOne() throws ParseException {
    long start = iso8601.parse("2016-11-1T01:01:01Z").getTime();
    long end = iso8601.parse("2016-11-3T23:59:59Z").getTime();

    assertThat(formatter.indexNamePatternsForRange(start, end))
        .containsExactly("zipkin-2016-11-01", "zipkin-2016-11-02", "zipkin-2016-11-03");
  }

  @Test
  public void indexNameForTimestampRange_nextMonth() throws ParseException {
    long start = iso8601.parse("2016-10-31T01:01:01Z").getTime();
    long end = iso8601.parse("2016-11-01T23:59:59Z").getTime();

    assertThat(formatter.indexNamePatternsForRange(start, end))
        .containsExactly("zipkin-2016-10-31", "zipkin-2016-11-01");
  }

  @Test
  public void indexNameForTimestampRange_compressesMonth() throws ParseException {
    long start = iso8601.parse("2016-10-01T01:01:01Z").getTime();
    long end = iso8601.parse("2016-10-31T23:59:59Z").getTime();

    assertThat(formatter.indexNamePatternsForRange(start, end))
        .containsExactly("zipkin-2016-10-*");
  }

  @Test
  public void indexNameForTimestampRange_skipMonths() throws ParseException {
    long start = iso8601.parse("2016-10-31T01:01:01Z").getTime();
    long end = iso8601.parse("2016-12-01T23:59:59Z").getTime();

    assertThat(formatter.indexNamePatternsForRange(start, end))
        .containsExactly("zipkin-2016-10-31", "zipkin-2016-11-*", "zipkin-2016-12-01");
  }

  @Test
  public void indexNameForTimestampRange_skipMonths_leapYear() throws ParseException {
    long start = iso8601.parse("2016-02-28T01:01:01Z").getTime();
    long end = iso8601.parse("2016-04-01T23:59:59Z").getTime();

    assertThat(formatter.indexNamePatternsForRange(start, end)).containsExactly(
        "zipkin-2016-02-28",
        "zipkin-2016-02-29",
        "zipkin-2016-03-*",
        "zipkin-2016-04-01"
    );
  }

  @Test
  public void indexNameForTimestampRange_compressesYear() throws ParseException {
    long start = iso8601.parse("2016-01-01T01:01:01Z").getTime();
    long end = iso8601.parse("2016-12-31T23:59:59Z").getTime();

    assertThat(formatter.indexNamePatternsForRange(start, end))
        .containsExactly("zipkin-2016-*");
  }

  @Test
  public void indexNameForTimestampRange_skipYears() throws ParseException {
    long start = iso8601.parse("2016-10-31T01:01:01Z").getTime();
    long end = iso8601.parse("2018-01-01T23:59:59Z").getTime();

    assertThat(formatter.indexNamePatternsForRange(start, end)).containsExactly(
        "zipkin-2016-10-31",
        "zipkin-2016-11-*",
        "zipkin-2016-12-*",
        "zipkin-2017-*",
        "zipkin-2018-01-01"
    );
  }
}
