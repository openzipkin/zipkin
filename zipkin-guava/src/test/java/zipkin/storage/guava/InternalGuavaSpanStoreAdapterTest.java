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
package zipkin.storage.guava;

import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.Callback;
import zipkin.storage.QueryRequest;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.isA;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static zipkin.TestObjects.LINKS;
import static zipkin.TestObjects.TRACE;

public class InternalGuavaSpanStoreAdapterTest {

  @Rule
  public MockitoRule mocks = MockitoJUnit.rule();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock
  private AsyncSpanStore asyncSpanStore;

  private GuavaSpanStore spanStore;

  @Before
  public void setUp() throws Exception {
    spanStore = new InternalGuavaSpanStoreAdapter(asyncSpanStore);
  }

  @Test
  public void getTraces_success() throws Exception {
    QueryRequest request = QueryRequest.builder().build();
    doAnswer(answer(c -> c.onSuccess(asList(TRACE))))
        .when(asyncSpanStore).getTraces(eq(request), any(Callback.class));

    assertThat(spanStore.getTraces(request).get()).containsExactly(TRACE);
  }

  @Test
  public void getTraces_exception() throws Exception {
    QueryRequest request = QueryRequest.builder().build();
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(asyncSpanStore).getTraces(eq(request), any(Callback.class));

    thrown.expect(ExecutionException.class);
    thrown.expectCause(isA(IllegalStateException.class));
    spanStore.getTraces(request).get();
  }

  @Test
  public void getTrace_success() throws Exception {
    doAnswer(answer(c -> c.onSuccess(TRACE)))
        .when(asyncSpanStore).getTrace(eq(1L), eq(2L), any(Callback.class));

    assertThat(spanStore.getTrace(1L, 2L).get()).isEqualTo(TRACE);
  }

  @Test
  public void getTrace_exception() throws Exception {
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(asyncSpanStore).getTrace(eq(1L), eq(2L), any(Callback.class));

    thrown.expect(ExecutionException.class);
    thrown.expectCause(isA(IllegalStateException.class));
    spanStore.getTrace(1L, 2L).get();
  }

  @Test
  public void getRawTrace_success() throws Exception {
    doAnswer(answer(c -> c.onSuccess(TRACE)))
        .when(asyncSpanStore).getRawTrace(eq(1L), eq(2L), any(Callback.class));

    assertThat(spanStore.getRawTrace(1L, 2L).get()).isEqualTo(TRACE);
  }

  @Test
  public void getRawTrace_exception() throws Exception {
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(asyncSpanStore).getRawTrace(eq(1L), eq(2L), any(Callback.class));

    thrown.expect(ExecutionException.class);
    thrown.expectCause(isA(IllegalStateException.class));
    spanStore.getRawTrace(1L, 2L).get();
  }

  @Test
  public void getServiceNames_success() throws Exception {
    doAnswer(answer(c -> c.onSuccess(asList("service1", "service2"))))
        .when(asyncSpanStore).getServiceNames(any(Callback.class));

    assertThat(spanStore.getServiceNames().get()).containsExactly("service1", "service2");
  }

  @Test
  public void getServiceNames_exception() throws Exception {
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(asyncSpanStore).getServiceNames(any(Callback.class));

    thrown.expect(ExecutionException.class);
    thrown.expectCause(isA(IllegalStateException.class));
    spanStore.getServiceNames().get();
  }

  @Test
  public void getSpanNames_success() throws Exception {
    doAnswer(answer(c -> c.onSuccess(asList("span1", "span2"))))
        .when(asyncSpanStore).getSpanNames(eq("service"), any(Callback.class));

    assertThat(spanStore.getSpanNames("service").get()).containsExactly("span1", "span2");
  }

  @Test
  public void getSpanNames_exception() throws Exception {
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(asyncSpanStore).getSpanNames(eq("service"), any(Callback.class));

    thrown.expect(ExecutionException.class);
    thrown.expectCause(isA(IllegalStateException.class));
    spanStore.getSpanNames("service").get();
  }

  @Test
  public void getDependencies_success() throws Exception {
    doAnswer(answer(c -> c.onSuccess(LINKS)))
        .when(asyncSpanStore).getDependencies(eq(1L), eq(0L), any(Callback.class));

    assertThat(spanStore.getDependencies(1L, 0L).get()).containsExactlyElementsOf(LINKS);
  }

  @Test
  public void getDependencies_exception() throws Exception {
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(asyncSpanStore).getDependencies(eq(1L), eq(0L), any(Callback.class));

    thrown.expect(ExecutionException.class);
    thrown.expectCause(isA(IllegalStateException.class));
    spanStore.getDependencies(1L, 0L).get();
  }

  static <T> Answer answer(Consumer<Callback<T>> onCallback) {
    return invocation -> {
      onCallback.accept((Callback) invocation.getArguments()[invocation.getArguments().length - 1]);
      return null;
    };
  }
}
