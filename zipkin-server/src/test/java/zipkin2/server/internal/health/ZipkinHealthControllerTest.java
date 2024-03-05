/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.health;

import java.util.List;
import org.junit.jupiter.api.Test;

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
      }"""
    );
  }

  @Test void writeJson_mappedByName() throws Exception {
    List<ComponentHealth> healths = List.of(
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
      }"""
    );
  }
}
