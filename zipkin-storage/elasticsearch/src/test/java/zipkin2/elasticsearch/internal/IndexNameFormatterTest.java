/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.internal;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IndexNameFormatterTest {
  IndexNameFormatter formatter =
      IndexNameFormatter.newBuilder().index("zipkin").dateSeparator('-').build();
  DateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

  public IndexNameFormatterTest() {
    iso8601.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  @Test void indexNameForTimestampRange_sameTime() throws ParseException {
    long start = iso8601.parse("2016-11-01T01:01:01Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, start))
      .containsExactly("zipkin*span-2016-11-01");
  }

  @Test void indexNameForTimestampRange_sameDay() throws ParseException {
    long start = iso8601.parse("2016-11-01T01:01:01Z").getTime();
    long end = iso8601.parse("2016-11-01T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly("zipkin*span-2016-11-01");
  }

  @Test void indexNameForTimestampRange_sameMonth() throws ParseException {
    long start = iso8601.parse("2016-11-15T01:01:01Z").getTime();
    long end = iso8601.parse("2016-11-16T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly("zipkin*span-2016-11-15", "zipkin*span-2016-11-16");
  }

  @Test void indexNameForTimestampRange_sameMonth_startingAtOne() throws ParseException {
    long start = iso8601.parse("2016-11-1T01:01:01Z").getTime();
    long end = iso8601.parse("2016-11-3T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly(
            "zipkin*span-2016-11-01", "zipkin*span-2016-11-02", "zipkin*span-2016-11-03");
  }

  @Test void indexNameForTimestampRange_nextMonth() throws ParseException {
    long start = iso8601.parse("2016-10-31T01:01:01Z").getTime();
    long end = iso8601.parse("2016-11-01T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly("zipkin*span-2016-10-31", "zipkin*span-2016-11-01");
  }

  @Test void indexNameForTimestampRange_compressesMonth() throws ParseException {
    long start = iso8601.parse("2016-10-01T01:01:01Z").getTime();
    long end = iso8601.parse("2016-10-31T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly("zipkin*span-2016-10-*");
  }

  @Test void indexNameForTimestampRange_skipMonths() throws ParseException {
    long start = iso8601.parse("2016-10-31T01:01:01Z").getTime();
    long end = iso8601.parse("2016-12-01T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly(
            "zipkin*span-2016-10-31", "zipkin*span-2016-11-*", "zipkin*span-2016-12-01");
  }

  @Test void indexNameForTimestampRange_skipMonths_leapYear() throws ParseException {
    long start = iso8601.parse("2016-02-28T01:01:01Z").getTime();
    long end = iso8601.parse("2016-04-01T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly(
            "zipkin*span-2016-02-28",
            "zipkin*span-2016-02-29",
            "zipkin*span-2016-03-*",
            "zipkin*span-2016-04-01");
  }

  @Test void indexNameForTimestampRange_compressesYear() throws ParseException {
    long start = iso8601.parse("2016-01-01T01:01:01Z").getTime();
    long end = iso8601.parse("2016-12-31T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly("zipkin*span-2016-*");
  }

  @Test void indexNameForTimestampRange_skipYears() throws ParseException {
    long start = iso8601.parse("2016-10-31T01:01:01Z").getTime();
    long end = iso8601.parse("2018-01-01T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly(
            "zipkin*span-2016-10-31",
            "zipkin*span-2016-11-*",
            "zipkin*span-2016-12-*",
            "zipkin*span-2017-*",
            "zipkin*span-2018-01-01");
  }

  @Test void indexNameForTimestampRange_other_sameDay() throws ParseException {
    formatter = formatter.toBuilder().dateSeparator('.').build();
    long start = iso8601.parse("2016-11-01T01:01:01Z").getTime();
    long end = iso8601.parse("2016-11-01T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly("zipkin*span-2016.11.01");
  }

  @Test void indexNameForTimestampRange_other_sameMonth() throws ParseException {
    formatter = formatter.toBuilder().dateSeparator('.').build();
    long start = iso8601.parse("2016-11-15T01:01:01Z").getTime();
    long end = iso8601.parse("2016-11-16T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly("zipkin*span-2016.11.15", "zipkin*span-2016.11.16");
  }

  @Test void indexNameForTimestampRange_sameMonth_other_startingAtOne() throws ParseException {
    formatter = formatter.toBuilder().dateSeparator('.').build();
    long start = iso8601.parse("2016-11-1T01:01:01Z").getTime();
    long end = iso8601.parse("2016-11-3T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly(
            "zipkin*span-2016.11.01", "zipkin*span-2016.11.02", "zipkin*span-2016.11.03");
  }

  @Test void indexNameForTimestampRange_other_nextMonth() throws ParseException {
    formatter = formatter.toBuilder().dateSeparator('.').build();
    long start = iso8601.parse("2016-10-31T01:01:01Z").getTime();
    long end = iso8601.parse("2016-11-01T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly("zipkin*span-2016.10.31", "zipkin*span-2016.11.01");
  }

  @Test void indexNameForTimestampRange_other_compressesMonth() throws ParseException {
    formatter = formatter.toBuilder().dateSeparator('.').build();
    long start = iso8601.parse("2016-10-01T01:01:01Z").getTime();
    long end = iso8601.parse("2016-10-31T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly("zipkin*span-2016.10.*");
  }

  @Test void indexNameForTimestampRange_other_skipMonths() throws ParseException {
    formatter = formatter.toBuilder().dateSeparator('.').build();
    long start = iso8601.parse("2016-10-31T01:01:01Z").getTime();
    long end = iso8601.parse("2016-12-01T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly(
            "zipkin*span-2016.10.31", "zipkin*span-2016.11.*", "zipkin*span-2016.12.01");
  }

  @Test void indexNameForTimestampRange_skipMonths_other_leapYear() throws ParseException {
    formatter = formatter.toBuilder().dateSeparator('.').build();
    long start = iso8601.parse("2016-02-28T01:01:01Z").getTime();
    long end = iso8601.parse("2016-04-01T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly(
            "zipkin*span-2016.02.28",
            "zipkin*span-2016.02.29",
            "zipkin*span-2016.03.*",
            "zipkin*span-2016.04.01");
  }

  @Test void indexNameForTimestampRange_other_compressesYear() throws ParseException {
    formatter = formatter.toBuilder().dateSeparator('.').build();
    long start = iso8601.parse("2016-01-01T01:01:01Z").getTime();
    long end = iso8601.parse("2016-12-31T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly("zipkin*span-2016.*");
  }

  @Test void indexNameForTimestampRange_other_skipYears() throws ParseException {
    formatter = formatter.toBuilder().dateSeparator('.').build();
    long start = iso8601.parse("2016-10-31T01:01:01Z").getTime();
    long end = iso8601.parse("2018-01-01T23:59:59Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
        .containsExactly(
            "zipkin*span-2016.10.31",
            "zipkin*span-2016.11.*",
            "zipkin*span-2016.12.*",
            "zipkin*span-2017.*",
            "zipkin*span-2018.01.01");
  }

  @Test void indexNameForTimestampRange_compressesTens() throws ParseException {
    formatter = formatter.toBuilder().dateSeparator('.').build();
    long start = iso8601.parse("2016-10-01T01:01:01Z").getTime();
    long end = iso8601.parse("2016-10-30T01:01:01Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
      .containsExactly(
        "zipkin*span-2016.10.0*",
        "zipkin*span-2016.10.1*",
        "zipkin*span-2016.10.2*",
        "zipkin*span-2016.10.30");
  }

  @Test void indexNameForTimestampRange_compressesTens_startingAtNine() throws ParseException {
    formatter = formatter.toBuilder().dateSeparator('.').build();
    long start = iso8601.parse("2016-10-09T01:01:01Z").getTime();
    long end = iso8601.parse("2016-10-30T01:01:01Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
      .containsExactly(
        "zipkin*span-2016.10.09",
        "zipkin*span-2016.10.1*",
        "zipkin*span-2016.10.2*",
        "zipkin*span-2016.10.30");
  }

  @Test void indexNameForTimestampRange_compressesTens_startingAtNineteen() throws ParseException {
    formatter = formatter.toBuilder().dateSeparator('.').build();
    long start = iso8601.parse("2016-10-19T01:01:01Z").getTime();
    long end = iso8601.parse("2016-10-30T01:01:01Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
      .containsExactly(
        "zipkin*span-2016.10.19",
        "zipkin*span-2016.10.2*",
        "zipkin*span-2016.10.30");
  }

  @Test void indexNameForTimestampRange_compressesTens_not30DayMonth() throws ParseException {
    formatter = formatter.toBuilder().dateSeparator('.').build();
    long start = iso8601.parse("2016-06-01T01:01:01Z").getTime();
    long end = iso8601.parse("2016-06-30T01:01:01Z").getTime();

    assertThat(formatter.formatTypeAndRange("span", start, end))
      .containsExactly("zipkin*span-2016.06.*");
  }
}
