/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage;

import java.util.List;
import org.junit.jupiter.api.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;

class GroupByTraceIdTest {
  Span oneOne = Span.newBuilder().traceId(1, 1).id(1).build();
  Span twoOne = Span.newBuilder().traceId(2, 1).id(1).build();
  Span zeroOne = Span.newBuilder().traceId(0, 1).id(1).build();

  @Test void map_groupsEverythingWhenNotStrict() {
    List<Span> spans = List.of(oneOne, twoOne, zeroOne);

    assertThat(GroupByTraceId.create(false).map(spans)).containsExactly(spans);
  }

  @Test void map_groupsByTraceIdHighWheStrict() {
    List<Span> spans = List.of(oneOne, twoOne, zeroOne);

    assertThat(GroupByTraceId.create(true).map(spans))
      .containsExactly(List.of(oneOne), List.of(twoOne), List.of(zeroOne));
  }

  @Test void map_modifiable() {
    List<Span> spans = List.of(oneOne, twoOne, zeroOne);

    List<List<Span>> modifiable = GroupByTraceId.create(true).map(spans);

    // This transform is used in cassandra v1 and filters when traces match on lower 64bits, but not
    // the higher ones.
    assertThat(StrictTraceId.filterTraces(List.of(twoOne.traceId())).map(modifiable))
      .containsExactly(List.of(twoOne));
  }
}
