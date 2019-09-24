/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.server.internal.health.ComponentHealth.STATUS_DOWN;
import static zipkin2.server.internal.health.ComponentHealth.STATUS_UP;

public class ZipkinHealthControllerTest {
  @Test public void writeJsonError_writesNestedError() throws Exception {
    assertThat(ZipkinHealthController.writeJsonError("robots")).isEqualTo(""
      + "{\n"
      + "  \"status\" : \"DOWN\",\n"
      + "  \"zipkin\" : {\n"
      + "    \"status\" : \"DOWN\",\n"
      + "    \"details\" : {\n"
      + "      \"error\" : \"robots\"\n"
      + "    }\n"
      + "  }\n"
      + "}"
    );
  }

  @Test public void writeJson_mappedByName() throws Exception {
    List<ComponentHealth> healths = asList(
      new ComponentHealth("foo", STATUS_UP, null),
      new ComponentHealth("bar", STATUS_DOWN, "java.io.IOException: socket disconnect")
    );
    assertThat(ZipkinHealthController.writeJson(STATUS_DOWN, healths)).isEqualTo(""
      + "{\n"
      + "  \"status\" : \"DOWN\",\n"
      + "  \"zipkin\" : {\n"
      + "    \"status\" : \"DOWN\",\n"
      + "    \"details\" : {\n"
      + "      \"foo\" : {\n"
      + "        \"status\" : \"UP\"\n"
      + "      },\n"
      + "      \"bar\" : {\n"
      + "        \"status\" : \"DOWN\",\n"
      + "        \"details\" : {\n"
      + "          \"error\" : \"java.io.IOException: socket disconnect\"\n"
      + "        }\n"
      + "      }\n"
      + "    }\n"
      + "  }\n"
      + "}"
    );
  }
}
