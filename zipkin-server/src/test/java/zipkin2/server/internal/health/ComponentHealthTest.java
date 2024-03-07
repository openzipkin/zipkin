/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.health;

import com.linecorp.armeria.common.ClosedSessionException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import zipkin2.CheckResult;
import zipkin2.Component;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentHealthTest {
  @Test void addsMessageToDetails() {
    ComponentHealth health = ComponentHealth.ofComponent(new Component() {
      @Override public CheckResult check() {
        return CheckResult.failed(new IOException("socket disconnect"));
      }
    });

    assertThat(health.error).isEqualTo("IOException: socket disconnect");
  }

  @Test void doesntAddNullMessageToDetails() {
    ComponentHealth health = ComponentHealth.ofComponent(new Component() {
      @Override public CheckResult check() {
        return CheckResult.failed(ClosedSessionException.get());
      }
    });

    assertThat(health.error).isEqualTo("ClosedSessionException");
  }
}
