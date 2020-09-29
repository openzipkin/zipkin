/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import java.nio.ByteBuffer;
import org.junit.Test;
import zipkin2.DependencyLink;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public final class DependenciesTest {
  @Test public void dependenciesRoundTrip() {
    DependencyLink ab = DependencyLink.newBuilder().parent("a").child("b").callCount(2L).build();
    DependencyLink cd = DependencyLink.newBuilder().parent("c").child("d").errorCount(2L).build();

    Dependencies dependencies = Dependencies.create(1L, 2L, asList(ab, cd));

    ByteBuffer bytes = dependencies.toThrift();
    assertThat(Dependencies.fromThrift(bytes)).isEqualTo(dependencies);
  }
}
