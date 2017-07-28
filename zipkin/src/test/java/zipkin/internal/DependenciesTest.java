/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.internal;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Test;
import zipkin.DependencyLink;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public final class DependenciesTest {
  @Test
  public void dependenciesRoundTrip() throws IOException {
    Dependencies dependencies = Dependencies.create(1L, 2L, asList(
      DependencyLink.builder().parent("foo").child("bar").callCount(2L).build(),
      DependencyLink.builder().parent("bar").child("baz").callCount(2L).errorCount(1L).build()
    ));

    ByteBuffer bytes = dependencies.toThrift();
    assertThat(Dependencies.fromThrift(bytes))
        .isEqualTo(dependencies);

    assertThat(bytes.remaining()).isZero();
  }
}
