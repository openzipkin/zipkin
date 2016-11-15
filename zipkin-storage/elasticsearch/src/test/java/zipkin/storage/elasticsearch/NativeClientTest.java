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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeClientTest {

  /**
   * The native client can only support the version matching the cluster. Since 2.x and 5.x classes
   * are incompatible, NativeClient is limited to the version it is built against: 2.x
   */
  @Test
  public void builderGetsVersionFromProperties() {
    NativeClient.Builder builder = new NativeClient.Builder();

    assertThat(builder.clientVersion).isNull(); // lazy init

    builder.buildFactory();
    assertThat(builder.clientVersion)
        .startsWith("2.4");
  }
}
