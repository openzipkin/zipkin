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
package zipkin2.server.internal.health;

import java.util.List;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.server.internal.health.ComponentHealth.STATUS_DOWN;
import static zipkin2.server.internal.health.ComponentHealth.STATUS_UP;

class ZipkinHealthControllerTest {
  @Test void writeJsonError_writesNestedError() throws Exception {
    assertThat(ZipkinHealthController.writeJsonError("robots")).isEqualTo("""
      {
        "status" : "DOWN",
        "zipkin" : {
          "status" : "DOWN",
          "details" : {
            "error" : "robots"
          }
        }
      }\
      """
    );
  }

  @Test void writeJson_mappedByName() throws Exception {
    List<ComponentHealth> healths = asList(
      new ComponentHealth("foo", STATUS_UP, null),
      new ComponentHealth("bar", STATUS_DOWN, "java.io.IOException: socket disconnect")
    );
    assertThat(ZipkinHealthController.writeJson(STATUS_DOWN, healths)).isEqualTo("""
      {
        "status" : "DOWN",
        "zipkin" : {
          "status" : "DOWN",
          "details" : {
            "foo" : {
              "status" : "UP"
            },
            "bar" : {
              "status" : "DOWN",
              "details" : {
                "error" : "java.io.IOException: socket disconnect"
              }
            }
          }
        }
      }\
      """
    );
  }
}
