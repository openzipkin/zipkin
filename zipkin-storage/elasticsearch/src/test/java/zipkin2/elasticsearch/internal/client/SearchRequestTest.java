/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.internal.client;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.elasticsearch.internal.JsonSerializers.OBJECT_MAPPER;

class SearchRequestTest {

  SearchRequest request = SearchRequest.create(List.of("zipkin-2016.11.31"));

  @Test void defaultSizeIsMaxResultWindow() {
    assertThat(request.size)
      .isEqualTo(10000);
  }

  /** Indices and type affect the request URI, not the json body */
  @Test void doesntSerializeIndicesOrType() throws Exception {
    assertThat(OBJECT_MAPPER.writeValueAsString(request))
      .isEqualTo("{\"size\":10000}");
  }
}
