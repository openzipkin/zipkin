/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.storage.elasticsearch;

import java.io.IOException;
import okio.Buffer;
import org.junit.Test;
import zipkin.DependencyLink;

import static org.assertj.core.api.Assertions.assertThat;

public final class InternalDependencyLinkAdapterTest {

  InternalDependencyLinkAdapter adapter = new InternalDependencyLinkAdapter();

  @Test
  public void dependencyLinkRoundTrip() throws IOException {
    DependencyLink link = DependencyLink.create("foo", "bar", 2);

    Buffer bytes = new Buffer();
    adapter.toJson(bytes, link);
    assertThat(adapter.fromJson(bytes))
        .isEqualTo(link);
  }
}
