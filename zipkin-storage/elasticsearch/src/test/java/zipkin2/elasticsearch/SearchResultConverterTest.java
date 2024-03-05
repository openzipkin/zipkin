/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch; // to access package private stuff

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import zipkin2.Annotation;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.elasticsearch.internal.JsonSerializers;
import zipkin2.elasticsearch.internal.client.SearchResultConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.elasticsearch.TestResponses.SPANS;
import static zipkin2.elasticsearch.internal.JsonSerializers.JSON_FACTORY;

class SearchResultConverterTest {
  SearchResultConverter<Span> converter = SearchResultConverter.create(JsonSerializers.SPAN_PARSER);

  @Test void convert() throws IOException {
    // Our normal test data has recent timestamps to make testing the server and dependency linker
    // work as there are values related to recency used in search defaults.
    // This test needs stable timestamps because items like MD5 need to match.
    long stableMicros = (TODAY - 1) * 1000L; // can't result in a zero value, so minimum ts of 1.
    List<Span> stableTrace = TestObjects.TRACE.stream()
      .map(s -> {
        Span.Builder builder = s.toBuilder().timestamp(s.timestampAsLong() - stableMicros)
          .clearAnnotations();
        for (Annotation a : s.annotations()) {
          builder.addAnnotation(a.timestamp() - stableMicros, a.value());
        }
        return builder.build();
      }).collect(Collectors.toList());
    assertThat(converter.convert(JSON_FACTORY.createParser(SPANS), Assertions::fail))
      .containsExactlyElementsOf(stableTrace);
  }

  @Test void convert_noHits() throws IOException {
    assertThat(converter.convert(JSON_FACTORY.createParser("{}"), Assertions::fail))
      .isEmpty();
  }

  @Test void convert_onlyOneLevelHits() throws IOException {
    assertThat(converter.convert(JSON_FACTORY.createParser("{\"hits\":{}}"), Assertions::fail))
      .isEmpty();
  }

  @Test void convert_hitsHitsButEmpty() throws IOException {
    assertThat(
      converter.convert(JSON_FACTORY.createParser("{\"hits\":{\"hits\":[]}}"), Assertions::fail))
      .isEmpty();
  }

  @Test void convert_hitsHitsButNoSource() throws IOException {
    assertThat(
      converter.convert(JSON_FACTORY.createParser("{\"hits\":{\"hits\":[{}]}}"), Assertions::fail))
      .isEmpty();
  }
}
