/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnnotationTest {

  @Test void messageWhenMissingValue() {
    Throwable exception = assertThrows(NullPointerException.class, () -> {

      Annotation.create(1L, null);
    });
    assertThat(exception.getMessage()).contains("value");
  }

  @Test void toString_isNice() {
    assertThat(Annotation.create(1L, "foo"))
      .hasToString("Annotation{timestamp=1, value=foo}");
  }
}
