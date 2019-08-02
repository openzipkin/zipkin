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
package zipkin2.elasticsearch; // to access package private stuff

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
import zipkin2.Annotation;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.elasticsearch.internal.JsonSerializers;
import zipkin2.elasticsearch.internal.client.SearchResultConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.TODAY;
import static zipkin2.elasticsearch.TestResponses.SPANS;
import static zipkin2.elasticsearch.internal.JsonSerializers.JSON_FACTORY;

public class SearchResultConverterTest {
  SearchResultConverter<Span> converter = SearchResultConverter.create(JsonSerializers.SPAN_PARSER);

  @Test public void convert() throws IOException {
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

  @Test public void convert_noHits() throws IOException {
    assertThat(converter.convert(JSON_FACTORY.createParser("{}"), Assertions::fail))
      .isEmpty();
  }

  @Test public void convert_onlyOneLevelHits() throws IOException {
    assertThat(converter.convert(JSON_FACTORY.createParser("{\"hits\":{}}"), Assertions::fail))
      .isEmpty();
  }

  @Test public void convert_hitsHitsButEmpty() throws IOException {
    assertThat(
      converter.convert(JSON_FACTORY.createParser("{\"hits\":{\"hits\":[]}}"), Assertions::fail))
      .isEmpty();
  }

  @Test public void convert_hitsHitsButNoSource() throws IOException {
    assertThat(
      converter.convert(JSON_FACTORY.createParser("{\"hits\":{\"hits\":[{}]}}"), Assertions::fail))
      .isEmpty();
  }
}
