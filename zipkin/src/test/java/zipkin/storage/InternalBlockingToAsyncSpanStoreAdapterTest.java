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
package zipkin.storage;

import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.CallbackCaptor;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static zipkin.TestObjects.LINKS;
import static zipkin.TestObjects.TRACE;

public class InternalBlockingToAsyncSpanStoreAdapterTest {

  @Rule
  public MockitoRule mocks = MockitoJUnit.rule();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock
  private SpanStore spanStore;

  private AsyncSpanStore asyncSpanStore;

  @Before
  public void setUp() {
    // run on calling thread so we don't have to make a complex callback captor
    asyncSpanStore = new InternalBlockingToAsyncSpanStoreAdapter(spanStore, Runnable::run);
  }

  @Test
  public void getTraces_success() {
    QueryRequest request = QueryRequest.builder().serviceName("zipkin-ui").build();
    when(spanStore.getTraces(request)).thenReturn(asList(TRACE));

    CallbackCaptor<List<List<Span>>> captor = new CallbackCaptor<>();
    asyncSpanStore.getTraces(request, captor);
    assertThat(captor.get()).isEqualTo(asList(TRACE));
  }

  @Test
  public void getTraces_exception() {
    thrown.expect(IllegalStateException.class);
    QueryRequest request = QueryRequest.builder().serviceName("service").build();
    when(spanStore.getTraces(request)).thenThrow(new IllegalStateException("failed"));

    CallbackCaptor<List<List<Span>>> captor = new CallbackCaptor<>();
    asyncSpanStore.getTraces(request, captor);
    captor.get();
  }

  @Test
  public void getTrace_success() {
    when(spanStore.getTrace(1L, 2L)).thenReturn(TRACE);

    CallbackCaptor<List<Span>> captor = new CallbackCaptor<>();
    asyncSpanStore.getTrace(1L, 2L, captor);
    assertThat(captor.get()).isEqualTo(TRACE);
  }

  @Test
  public void getTrace_exception() {
    thrown.expect(IllegalStateException.class);
    when(spanStore.getTrace(1L, 2L)).thenThrow(new IllegalStateException("failed"));

    CallbackCaptor<List<Span>> captor = new CallbackCaptor<>();
    asyncSpanStore.getTrace(1L, 2L, captor);
    captor.get();
  }

  @Test
  public void getRawTrace_success() {
    when(spanStore.getRawTrace(1L, 2L)).thenReturn(TRACE);

    CallbackCaptor<List<Span>> captor = new CallbackCaptor<>();
    asyncSpanStore.getRawTrace(1L, 2L, captor);
    assertThat(captor.get()).isEqualTo(TRACE);
  }

  @Test
  public void getRawTrace_exception() {
    thrown.expect(IllegalStateException.class);
    when(spanStore.getRawTrace(1L, 2L)).thenThrow(new IllegalStateException("failed"));

    CallbackCaptor<List<Span>> captor = new CallbackCaptor<>();
    asyncSpanStore.getRawTrace(1L, 2L, captor);
    captor.get();
  }

  @Test
  public void getServiceNames_success() {
    when(spanStore.getServiceNames()).thenReturn(asList("service1"));

    CallbackCaptor<List<String>> captor = new CallbackCaptor<>();
    asyncSpanStore.getServiceNames(captor);
    assertThat(captor.get()).isEqualTo(asList("service1"));
  }

  @Test
  public void getServiceNames_exception() {
    thrown.expect(IllegalStateException.class);
    when(spanStore.getServiceNames()).thenThrow(new IllegalStateException("failed"));

    CallbackCaptor<List<String>> captor = new CallbackCaptor<>();
    asyncSpanStore.getServiceNames(captor);
    captor.get();
  }

  @Test
  public void getSpanNames_success() {
    when(spanStore.getSpanNames("service1")).thenReturn(asList("span1"));

    CallbackCaptor<List<String>> captor = new CallbackCaptor<>();
    asyncSpanStore.getSpanNames("service1", captor);
    assertThat(captor.get()).isEqualTo(asList("span1"));
  }

  @Test
  public void getSpanNames_exception() {
    thrown.expect(IllegalStateException.class);
    when(spanStore.getSpanNames("service1")).thenThrow(new IllegalStateException("failed"));

    CallbackCaptor<List<String>> captor = new CallbackCaptor<>();
    asyncSpanStore.getSpanNames("service1", captor);
    captor.get();
  }

  @Test
  public void getDependencies_success() {
    when(spanStore.getDependencies(1L, null)).thenReturn(LINKS);

    CallbackCaptor<List<DependencyLink>> captor = new CallbackCaptor<>();
    asyncSpanStore.getDependencies(1L, null, captor);
    assertThat(captor.get()).isEqualTo(LINKS);
  }

  @Test
  public void getDependencies_exception() {
    thrown.expect(IllegalStateException.class);
    when(spanStore.getDependencies(1L, null)).thenThrow(new IllegalStateException("failed"));

    CallbackCaptor<List<DependencyLink>> captor = new CallbackCaptor<>();
    asyncSpanStore.getDependencies(1L, null, captor);
    captor.get();
  }
}
