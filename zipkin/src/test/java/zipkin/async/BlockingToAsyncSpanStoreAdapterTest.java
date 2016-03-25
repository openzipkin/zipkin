/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.async;

import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import zipkin.DependencyLink;
import zipkin.QueryRequest;
import zipkin.Span;
import zipkin.SpanStore;
import zipkin.internal.Nullable;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static zipkin.TestObjects.LINKS;
import static zipkin.TestObjects.TRACE;

public class BlockingToAsyncSpanStoreAdapterTest {

  @Rule
  public MockitoRule mocks = MockitoJUnit.rule();

  @Mock
  private SpanStore delegate;

  private AsyncSpanStore spanStore;

  @Before
  public void setUp() {
    // run on calling thread so we don't have to make a complex callback captor
    spanStore = new BlockingToAsyncSpanStoreAdapter(delegate, new Executor() {
      @Override public void execute(Runnable command) {
        command.run();
      }
    });
  }

  @Test
  public void getTraces_success() {
    QueryRequest request = new QueryRequest.Builder("zipkin-web").build();
    when(delegate.getTraces(request)).thenReturn(asList(TRACE));

    CallbackCaptor<List<List<Span>>> captor = new CallbackCaptor<>();
    spanStore.getTraces(request, captor);
    assertThat(captor.get()).isEqualTo(asList(TRACE));
  }

  @Test
  public void getTraces_exception() {
    QueryRequest request = new QueryRequest.Builder("service").build();
    when(delegate.getTraces(request)).thenThrow(new IllegalStateException("failed"));

    CallbackCaptor<List<List<Span>>> captor = new CallbackCaptor<>();
    spanStore.getTraces(request, captor);
    assertThat(captor.get()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void getTrace_success() {
    when(delegate.getTrace(1L)).thenReturn(TRACE);

    CallbackCaptor<List<Span>> captor = new CallbackCaptor<>();
    spanStore.getTrace(1L, captor);
    assertThat(captor.get()).isEqualTo(TRACE);
  }

  @Test
  public void getTrace_exception() {
    when(delegate.getTrace(1L)).thenThrow(new IllegalStateException("failed"));

    CallbackCaptor<List<Span>> captor = new CallbackCaptor<>();
    spanStore.getTrace(1L, captor);
    assertThat(captor.get()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void getRawTrace_success() {
    when(delegate.getRawTrace(1L)).thenReturn(TRACE);

    CallbackCaptor<List<Span>> captor = new CallbackCaptor<>();
    spanStore.getRawTrace(1L, captor);
    assertThat(captor.get()).isEqualTo(TRACE);
  }

  @Test
  public void getRawTrace_exception() {
    when(delegate.getRawTrace(1L)).thenThrow(new IllegalStateException("failed"));

    CallbackCaptor<List<Span>> captor = new CallbackCaptor<>();
    spanStore.getRawTrace(1L, captor);
    assertThat(captor.get()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void getServiceNames_success() {
    when(delegate.getServiceNames()).thenReturn(asList("service1"));

    CallbackCaptor<List<String>> captor = new CallbackCaptor<>();
    spanStore.getServiceNames(captor);
    assertThat(captor.get()).isEqualTo(asList("service1"));
  }

  @Test
  public void getServiceNames_exception() {
    when(delegate.getServiceNames()).thenThrow(new IllegalStateException("failed"));

    CallbackCaptor<List<String>> captor = new CallbackCaptor<>();
    spanStore.getServiceNames(captor);
    assertThat(captor.get()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void getSpanNames_success() {
    when(delegate.getSpanNames("service1")).thenReturn(asList("span1"));

    CallbackCaptor<List<String>> captor = new CallbackCaptor<>();
    spanStore.getSpanNames("service1", captor);
    assertThat(captor.get()).isEqualTo(asList("span1"));
  }

  @Test
  public void getSpanNames_exception() {
    when(delegate.getSpanNames("service1")).thenThrow(new IllegalStateException("failed"));

    CallbackCaptor<List<String>> captor = new CallbackCaptor<>();
    spanStore.getSpanNames("service1", captor);
    assertThat(captor.get()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void getDependencies_success() {
    when(delegate.getDependencies(1L, null)).thenReturn(LINKS);

    CallbackCaptor<List<DependencyLink>> captor = new CallbackCaptor<>();
    spanStore.getDependencies(1L, null, captor);
    assertThat(captor.get()).isEqualTo(LINKS);
  }

  @Test
  public void getDependencies_exception() {
    when(delegate.getDependencies(1L, null)).thenThrow(new IllegalStateException("failed"));

    CallbackCaptor<List<DependencyLink>> captor = new CallbackCaptor<>();
    spanStore.getDependencies(1L, null, captor);
    assertThat(captor.get()).isInstanceOf(IllegalStateException.class);
  }

  private static final class CallbackCaptor<V> implements Callback<V> {
    Object ref = null;

    @Nullable Object get() {
      return ref;
    }

    @Override public void onSuccess(@Nullable V value) {
      ref = value;
    }

    @Override public void onError(Throwable t) {
      ref = t;
    }
  }
}
