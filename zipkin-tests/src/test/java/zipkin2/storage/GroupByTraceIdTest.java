/*
 * Copyright 2015-2023 The OpenZipkin Authors
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
package zipkin2.storage;

import java.util.List;
import org.junit.jupiter.api.Test;
import zipkin2.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class GroupByTraceIdTest {
  Span oneOne = Span.newBuilder().traceId(1, 1).id(1).build();
  Span twoOne = Span.newBuilder().traceId(2, 1).id(1).build();
  Span zeroOne = Span.newBuilder().traceId(0, 1).id(1).build();

  @Test void map_groupsEverythingWhenNotStrict() {
    List<Span> spans = asList(oneOne, twoOne, zeroOne);

    assertThat(GroupByTraceId.create(false).map(spans)).containsExactly(spans);
  }

  @Test void map_groupsByTraceIdHighWheStrict() {
    List<Span> spans = asList(oneOne, twoOne, zeroOne);

    assertThat(GroupByTraceId.create(true).map(spans))
      .containsExactly(asList(oneOne), asList(twoOne), asList(zeroOne));
  }

  @Test void map_modifiable() {
    List<Span> spans = asList(oneOne, twoOne, zeroOne);

    List<List<Span>> modifiable = GroupByTraceId.create(true).map(spans);

    // This transform is used in cassandra v1 and filters when traces match on lower 64bits, but not
    // the higher ones.
    assertThat(StrictTraceId.filterTraces(asList(twoOne.traceId())).map(modifiable))
      .containsExactly(asList(twoOne));
  }
}
