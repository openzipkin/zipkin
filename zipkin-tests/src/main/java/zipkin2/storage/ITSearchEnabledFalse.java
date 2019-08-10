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
import org.junit.jupiter.api.Test;
import zipkin2.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.storage.ITSpanStore.requestBuilder;

/**
 * Base test for when {@link StorageComponent.Builder#searchEnabled(boolean) searchEnabled ==
 * false}.
 *
 * <p>Subtypes should create a connection to a real backend, even if that backend is in-process.
 */
public abstract class ITSearchEnabledFalse<T extends StorageComponent> extends ITStorage<T> {

  @Override protected final void configureStorageForTest(StorageComponent.Builder storage) {
    storage.searchEnabled(false);
  }

  @Test void getTraces_indexDataReturnsNothing() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(store().getTraces(requestBuilder()
      .build()).execute()).isEmpty();

    assertThat(store().getTraces(requestBuilder()
      .serviceName(CLIENT_SPAN.localServiceName())
      .build()).execute()).isEmpty();

    assertThat(store().getTraces(requestBuilder()
      .spanName(CLIENT_SPAN.name())
      .build()).execute()).isEmpty();

    assertThat(store().getTraces(requestBuilder()
      .annotationQuery(CLIENT_SPAN.tags())
      .build()).execute()).isEmpty();

    assertThat(store().getTraces(requestBuilder()
      .minDuration(CLIENT_SPAN.duration())
      .build()).execute()).isEmpty();
  }

  @Test void getServiceNames_isEmpty() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(names().getServiceNames().execute()).isEmpty();
  }

  @Test void getRemoteServiceNames_isEmpty() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(names().getRemoteServiceNames(CLIENT_SPAN.localServiceName()).execute()).isEmpty();
  }

  @Test void getSpanNames_isEmpty() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(names().getSpanNames(CLIENT_SPAN.localServiceName()).execute()).isEmpty();
  }

  protected void accept(Span... spans) throws IOException {
    spanConsumer().accept(asList(spans)).execute();
  }
}
