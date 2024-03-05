/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

import java.nio.ByteBuffer;
import java.util.List;
import org.junit.jupiter.api.Test;
import zipkin2.DependencyLink;

import static org.assertj.core.api.Assertions.assertThat;

final class DependenciesTest {
  @Test void dependenciesRoundTrip() {
    DependencyLink ab = DependencyLink.newBuilder().parent("a").child("b").callCount(2L).build();
    DependencyLink cd = DependencyLink.newBuilder().parent("c").child("d").errorCount(2L).build();

    Dependencies dependencies = Dependencies.create(1L, 2L, List.of(ab, cd));

    ByteBuffer bytes = dependencies.toThrift();
    assertThat(Dependencies.fromThrift(bytes)).isEqualTo(dependencies);
  }
}
