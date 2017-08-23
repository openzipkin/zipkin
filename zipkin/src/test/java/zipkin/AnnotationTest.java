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
package zipkin;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.APP_ENDPOINT;

public class AnnotationTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  @Test public void messageWhenMissingValue() {
    thrown.expect(NullPointerException.class);
    thrown.expectMessage("value");

    Annotation.create(1L, null, null);
  }

  @Test public void toStringIsJson_withEndpoint() {
    assertThat(Annotation.create(1L, "foo", APP_ENDPOINT))
      .hasToString(
        "{\"timestamp\":1,\"value\":\"foo\",\"endpoint\":{\"serviceName\":\"app\",\"ipv4\":\"172.17.0.2\",\"port\":8080}}");
  }

  // v2 spans will not have endpoint defined eventhough normal ones will
  @Test public void toStringIsJson_withoutEndpoint() {
    assertThat(Annotation.create(1L, "foo", null))
      .hasToString("{\"timestamp\":1,\"value\":\"foo\"}");
  }
}
