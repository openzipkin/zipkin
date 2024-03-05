/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.storage.QueryRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.TODAY;

class FilterTracesTest {
  QueryRequest request = QueryRequest.newBuilder().endTs(TODAY).lookback(1).limit(1).build();

  @Test void returnsWhenValidlyMatches() {
    List<List<Span>> input = new ArrayList<>(List.of(TestObjects.TRACE));

    assertThat(FilterTraces.create(request).map(input)).isEqualTo(input);
  }

  @Test void doesntMutateInputWhenUnmatched() {
    List<List<Span>> input = List.of(TestObjects.TRACE);

    assertThat(FilterTraces.create(request.toBuilder().endTs(1).build()).map(input))
      .isEmpty();
  }
}
