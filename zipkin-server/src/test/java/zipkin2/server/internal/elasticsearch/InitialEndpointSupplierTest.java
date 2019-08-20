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
