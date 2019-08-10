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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
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
public abstract class ITServiceAndSpanNames<T extends StorageComponent> extends ITStorage<T> {

  @Override protected final void configureStorageForTest(StorageComponent.Builder storage) {
    // Defaults are fine.
  }

  @Test void getLocalServiceNames_includesLocalServiceName() throws Exception {
    assertThat(names().getServiceNames().execute())
      .isEmpty();

    accept(CLIENT_SPAN);

    assertThat(names().getServiceNames().execute())
      .containsOnly("frontend");
  }

  @Test void getLocalServiceNames_noServiceName() throws IOException {
    accept(Span.newBuilder().traceId("a").id("a").build());

    assertThat(names().getServiceNames().execute()).isEmpty();
  }

  @Test void getRemoteServiceNames() throws Exception {
    assertThat(names().getRemoteServiceNames("frontend").execute())
      .isEmpty();

    accept(CLIENT_SPAN);

    assertThat(names().getRemoteServiceNames("frontend" + 1).execute())
      .isEmpty();

    assertThat(names().getRemoteServiceNames("frontend").execute())
      .contains(CLIENT_SPAN.remoteServiceName());
  }

  @Test void getRemoteServiceNames_allReturned() throws IOException {
    // Assure a default store limit isn't hit by assuming if 50 are returned, all are returned
    List<Span> spans = IntStream.rangeClosed(0, 50)
      .mapToObj(i -> {
        String suffix = i < 10 ? "0" + i : String.valueOf(i);
        return CLIENT_SPAN.toBuilder()
          .id(i + 1)
          .remoteEndpoint(Endpoint.newBuilder().serviceName("yak" + suffix).build())
          .build();
      })
      .collect(Collectors.toList());
    accept(spans);

    assertThat(names().getRemoteServiceNames("frontend").execute())
      .containsExactlyInAnyOrderElementsOf(spans.stream().map(Span::remoteServiceName)::iterator);
  }

  /** Ensures the service name index returns distinct results */
  @Test void getRemoteServiceNames_dedupes() throws IOException {
    List<Span> spans = IntStream.rangeClosed(0, 50)
      .mapToObj(i -> CLIENT_SPAN.toBuilder().id(i + 1).build())
      .collect(Collectors.toList());
    accept(spans);

    assertThat(names().getRemoteServiceNames("frontend").execute())
      .containsExactly(CLIENT_SPAN.remoteServiceName());
  }

  @Test void getRemoteServiceNames_noRemoteServiceName() throws IOException {
    accept(Span.newBuilder().traceId("a").id("a").localEndpoint(FRONTEND).build());

    assertThat(names().getRemoteServiceNames("frontend").execute()).isEmpty();
  }

  @Test void getRemoteServiceNames_serviceNameGoesLowercase() throws IOException {
    accept(CLIENT_SPAN);

    assertThat(names().getRemoteServiceNames("FrOnTeNd").execute())
      .containsExactly(CLIENT_SPAN.remoteServiceName());
  }

  @Test void getSpanNames_doesNotMapNameToRemoteServiceName() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(names().getSpanNames(CLIENT_SPAN.remoteServiceName()).execute())
      .isEmpty();
  }

  @Test void getSpanNames() throws Exception {
    assertThat(names().getSpanNames("frontend").execute())
      .isEmpty();

    accept(CLIENT_SPAN);

    assertThat(names().getSpanNames("frontend" + 1).execute())
      .isEmpty();

    assertThat(names().getSpanNames("frontend").execute())
      .contains(CLIENT_SPAN.name());
  }

  @Test void getSpanNames_allReturned() throws IOException {
    // Assure a default store limit isn't hit by assuming if 50 are returned, all are returned
    List<Span> spans = IntStream.rangeClosed(0, 50)
      .mapToObj(i -> {
        String suffix = i < 10 ? "0" + i : String.valueOf(i);
        return CLIENT_SPAN.toBuilder().id(i + 1).name("yak" + suffix).build();
      })
      .collect(Collectors.toList());
    accept(spans);

    assertThat(names().getSpanNames("frontend").execute())
      .containsExactlyInAnyOrderElementsOf(spans.stream().map(Span::name)::iterator);
  }

  /** Ensures the span name index returns distinct results */
  @Test void getSpanNames_dedupes() throws IOException {
    List<Span> spans = IntStream.rangeClosed(0, 50)
      .mapToObj(i -> CLIENT_SPAN.toBuilder().id(i + 1).build())
      .collect(Collectors.toList());
    accept(spans);

    assertThat(names().getSpanNames("frontend").execute())
      .containsExactly(CLIENT_SPAN.name());
  }

  @Test void getSpanNames_noSpanName() throws IOException {
    accept(Span.newBuilder().traceId("a").id("a").localEndpoint(FRONTEND).build());

    assertThat(names().getSpanNames("frontend").execute()).isEmpty();
  }

  @Test void getSpanNames_serviceNameGoesLowercase() throws IOException {
    accept(CLIENT_SPAN);

    assertThat(names().getSpanNames("FrOnTeNd").execute()).containsExactly("get");
  }

  protected void accept(List<Span> spans) throws IOException {
    spanConsumer().accept(spans).execute();
  }

  protected void accept(Span... spans) throws IOException {
    spanConsumer().accept(asList(spans)).execute();
  }
}
