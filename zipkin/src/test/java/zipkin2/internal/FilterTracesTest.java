/*
 * Copyright 2015-2024 The OpenZipkin Authors
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
