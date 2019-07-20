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

import com.linecorp.armeria.common.HttpData;
import java.io.IOException;
import org.junit.Test;
import zipkin2.TestObjects;
import zipkin2.elasticsearch.internal.JsonSerializers;
import zipkin2.elasticsearch.internal.client.SearchResultConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.elasticsearch.TestResponses.SPANS;

public class SearchResultConverterTest {
  SearchResultConverter converter = SearchResultConverter.create(JsonSerializers.SPAN_PARSER);

  @Test public void convert() throws IOException {
    assertThat(converter.convert(HttpData.ofUtf8(SPANS)))
      .containsExactlyInAnyOrderElementsOf(TestObjects.TRACE);
  }

  @Test public void convert_noHits() throws IOException {
    assertThat(converter.convert(HttpData.ofUtf8("{}")))
      .isEmpty();
  }

  @Test public void convert_onlyOneLevelHits() throws IOException {
    assertThat(converter.convert(HttpData.ofUtf8("{\"hits\":{}}")))
      .isEmpty();
  }

  @Test public void convert_hitsHitsButEmpty() throws IOException {
    assertThat(converter.convert(HttpData.ofUtf8("{\"hits\":{\"hits\":[]}}")))
      .isEmpty();
  }

  @Test public void convert_hitsHitsButNoSource() throws IOException {
    assertThat(converter.convert(HttpData.ofUtf8("{\"hits\":{\"hits\":[{}]}}")))
      .isEmpty();
  }
}
