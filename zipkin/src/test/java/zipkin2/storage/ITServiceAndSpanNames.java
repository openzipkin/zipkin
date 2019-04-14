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
package zipkin2.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import zipkin2.Endpoint;
import zipkin2.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.FRONTEND;

/**
 * Base test for {@link ServiceAndSpanNames}.
 *
 * <p>Subtypes should create a connection to a real backend, even if that backend is in-process.
 */
public abstract class ITServiceAndSpanNames {

  /** Should maintain state between multiple calls within a test. */
  protected abstract StorageComponent storage();

  protected ServiceAndSpanNames serviceAndSpanNames() {
    return storage().serviceAndSpanNames();
  }

  /** Clears serviceAndSpanNames between tests. */
  @Before public abstract void clear() throws Exception;

  @Test public void getLocalServiceNames_includesLocalServiceName() throws Exception {
    assertThat(serviceAndSpanNames().getServiceNames().execute())
      .isEmpty();

    accept(CLIENT_SPAN);

    assertThat(serviceAndSpanNames().getServiceNames().execute())
      .containsOnly("frontend");
  }

  @Test public void getLocalServiceNames_noServiceName() throws IOException {
    accept(Span.newBuilder().traceId("a").id("a").build());

    assertThat(serviceAndSpanNames().getServiceNames().execute()).isEmpty();
  }

  @Test public void getRemoteServiceNames() throws Exception {
    assertThat(serviceAndSpanNames().getRemoteServiceNames("frontend").execute())
      .isEmpty();

    accept(CLIENT_SPAN);

    assertThat(serviceAndSpanNames().getRemoteServiceNames("frontend" + 1).execute())
      .isEmpty();

    assertThat(serviceAndSpanNames().getRemoteServiceNames("frontend").execute())
      .contains(CLIENT_SPAN.remoteServiceName());
  }

  @Test public void getRemoteServiceNames_allReturned() throws IOException {
    // Assure a default store limit isn't hit by assuming if 50 are returned, all are returned
    List<String> remoteServiceNames = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      String suffix = i < 10 ? "0" + i : String.valueOf(i);
      accept(CLIENT_SPAN.toBuilder()
        .id(i + 1)
        .remoteEndpoint(Endpoint.newBuilder().serviceName("yak" + suffix).build())
        .build());
      remoteServiceNames.add("yak" + suffix);
    }

    assertThat(serviceAndSpanNames().getRemoteServiceNames("frontend").execute())
      .containsExactlyInAnyOrderElementsOf(remoteServiceNames);
  }

  /** Ensures the service name index returns distinct results */
  @Test public void getRemoteServiceNames_dedupes() throws IOException {
    for (int i = 0; i < 50; i++) accept(CLIENT_SPAN.toBuilder().id(i + 1).build());

    assertThat(serviceAndSpanNames().getRemoteServiceNames("frontend").execute())
      .containsExactly(CLIENT_SPAN.remoteServiceName());
  }

  @Test public void getRemoteServiceNames_noRemoteServiceName() throws IOException {
    accept(Span.newBuilder().traceId("a").id("a").localEndpoint(FRONTEND).build());

    assertThat(serviceAndSpanNames().getRemoteServiceNames("frontend").execute()).isEmpty();
  }

  @Test public void getRemoteServiceNames_serviceNameGoesLowercase() throws IOException {
    accept(CLIENT_SPAN);

    assertThat(serviceAndSpanNames().getRemoteServiceNames("FrOnTeNd").execute())
      .containsExactly(CLIENT_SPAN.remoteServiceName());
  }

  @Test public void getSpanNames_doesNotMapNameToRemoteServiceName() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(serviceAndSpanNames().getSpanNames(CLIENT_SPAN.remoteServiceName()).execute())
      .isEmpty();
  }

  @Test public void getSpanNames() throws Exception {
    assertThat(serviceAndSpanNames().getSpanNames("frontend").execute())
      .isEmpty();

    accept(CLIENT_SPAN);

    assertThat(serviceAndSpanNames().getSpanNames("frontend" + 1).execute())
      .isEmpty();

    assertThat(serviceAndSpanNames().getSpanNames("frontend").execute())
      .contains(CLIENT_SPAN.name());
  }

  @Test public void getSpanNames_allReturned() throws IOException {
    // Assure a default store limit isn't hit by assuming if 50 are returned, all are returned
    List<String> spanNames = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      String suffix = i < 10 ? "0" + i : String.valueOf(i);
      accept(CLIENT_SPAN.toBuilder().id(i + 1).name("yak" + suffix).build());
      spanNames.add("yak" + suffix);
    }

    assertThat(serviceAndSpanNames().getSpanNames("frontend").execute())
      .containsExactlyInAnyOrderElementsOf(spanNames);
  }

  /** Ensures the span name index returns distinct results */
  @Test public void getSpanNames_dedupes() throws IOException {
    for (int i = 0; i < 50; i++) accept(CLIENT_SPAN.toBuilder().id(i + 1).build());

    assertThat(serviceAndSpanNames().getSpanNames("frontend").execute())
      .containsExactly(CLIENT_SPAN.name());
  }

  @Test public void getSpanNames_noSpanName() throws IOException {
    accept(Span.newBuilder().traceId("a").id("a").localEndpoint(FRONTEND).build());

    assertThat(serviceAndSpanNames().getSpanNames("frontend").execute()).isEmpty();
  }

  @Test public void getSpanNames_serviceNameGoesLowercase() throws IOException {
    accept(CLIENT_SPAN);

    assertThat(serviceAndSpanNames().getSpanNames("FrOnTeNd").execute()).containsExactly("get");
  }

  protected void accept(List<Span> spans) throws IOException {
    storage().spanConsumer().accept(spans).execute();
  }

  protected void accept(Span... spans) throws IOException {
    storage().spanConsumer().accept(asList(spans)).execute();
  }
}
