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
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.endpoint.DynamicEndpointGroup;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InitialEndpointSupplierTest {
  static final Endpoint FOO = Endpoint.of("foo");
  static final Endpoint BAR = Endpoint.of("bar");
  static final Endpoint CAT = Endpoint.of("cat");
  static final Endpoint DOG = Endpoint.of("dog");

  // Simple class to give public access to methods.
  static class PublicDynamicEndpointGroup extends DynamicEndpointGroup {
    void doSetEndpoints(Endpoint... endpoints) {
      setEndpoints(Arrays.asList(endpoints));
    }
  }

  @Test void compositeEndpoints() {
    PublicDynamicEndpointGroup group1 = new PublicDynamicEndpointGroup();
    PublicDynamicEndpointGroup group2 = new PublicDynamicEndpointGroup();

    InitialEndpointSupplier.CompositeEndpointGroup composite =
      new InitialEndpointSupplier.CompositeEndpointGroup(Arrays.asList(group1, group2));
    assertThat(composite.endpoints()).isEmpty();

    group1.doSetEndpoints(FOO, BAR);
    assertThat(composite.endpoints()).containsExactlyInAnyOrder(FOO, BAR);
    // Return memoized endpoints.
    assertThat(composite.endpoints()).isSameAs(composite.endpoints());
    group2.doSetEndpoints(CAT, DOG);
    assertThat(composite.endpoints()).containsExactlyInAnyOrder(FOO, BAR, CAT, DOG);
    group1.doSetEndpoints(FOO);
    assertThat(composite.endpoints()).containsExactlyInAnyOrder(FOO, CAT, DOG);
  }

}
