/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.storage;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
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
public abstract class ITSearchEnabledFalse {

  /** Should maintain state between multiple calls within a test. */
  protected abstract StorageComponent storage();

  protected SpanStore store() {
    return storage().spanStore();
  }

  protected ServiceAndSpanNames names() {
    return storage().serviceAndSpanNames();
  }

  /** Clears store between tests. */
  @Before public abstract void clear() throws Exception;

  @Test public void getTraces_indexDataReturnsNothing() throws Exception {
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

  @Test public void getServiceNames_isEmpty() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(names().getServiceNames().execute()).isEmpty();
  }

  @Test public void getRemoteServiceNames_isEmpty() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(names().getRemoteServiceNames(CLIENT_SPAN.localServiceName()).execute()).isEmpty();
  }

  @Test public void getSpanNames_isEmpty() throws Exception {
    accept(CLIENT_SPAN);

    assertThat(names().getSpanNames(CLIENT_SPAN.localServiceName()).execute()).isEmpty();
  }

  protected void accept(Span... spans) throws IOException {
    storage().spanConsumer().accept(asList(spans)).execute();
  }
}
