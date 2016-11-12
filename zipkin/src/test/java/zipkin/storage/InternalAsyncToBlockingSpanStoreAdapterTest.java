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

import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static zipkin.TestObjects.LINKS;
import static zipkin.TestObjects.TRACE;

public class InternalAsyncToBlockingSpanStoreAdapterTest {

  @Rule
  public MockitoRule mocks = MockitoJUnit.rule();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock
  private AsyncSpanStore delegate;

  private InternalAsyncToBlockingSpanStoreAdapter spanStore;

  @Before
  public void setUp() {
    spanStore = new InternalAsyncToBlockingSpanStoreAdapter(delegate);
  }

  @Test
  public void getTraces_success() {
    QueryRequest request = QueryRequest.builder().serviceName("service").endTs(1000L).build();
    doAnswer(answer(c -> c.onSuccess(asList(TRACE))))
        .when(delegate).getTraces(eq(request), any(Callback.class));

    assertThat(spanStore.getTraces(request)).containsExactly(TRACE);
  }


  @Test
  public void getTraces_exception() {
    QueryRequest request = QueryRequest.builder().serviceName("service").endTs(1000L).build();
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(delegate).getTraces(eq(request), any(Callback.class));

    thrown.expect(IllegalStateException.class);
    spanStore.getTraces(request);
  }

  @Test
  public void getTrace_success() {
    doAnswer(answer(c -> c.onSuccess(TRACE)))
        .when(delegate).getTrace(eq(1L), eq(2L), any(Callback.class));

    assertThat(spanStore.getTrace(1L, 2L)).isEqualTo(TRACE);
  }

  @Test
  public void getTrace_exception() {
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(delegate).getTrace(eq(1L), eq(2L), any(Callback.class));

    thrown.expect(IllegalStateException.class);
    spanStore.getTrace(1L, 2L);
  }

  @Test
  public void getRawTrace_success() {
    doAnswer(answer(c -> c.onSuccess(TRACE)))
        .when(delegate).getRawTrace(eq(1L), eq(2L), any(Callback.class));

    assertThat(spanStore.getRawTrace(1L, 2L)).isEqualTo(TRACE);
  }

  @Test
  public void getRawTrace_exception() {
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(delegate).getRawTrace(eq(1L), eq(2L), any(Callback.class));

    thrown.expect(IllegalStateException.class);;
    spanStore.getRawTrace(1L, 2L);
  }

  @Test
  public void getServiceNames_success() {
    doAnswer(answer(c -> c.onSuccess(asList("service1", "service2"))))
        .when(delegate).getServiceNames(any(Callback.class));

    assertThat(spanStore.getServiceNames()).containsExactly("service1", "service2");
  }

  @Test
  public void getServiceNames_exception() {
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(delegate).getServiceNames(any(Callback.class));

    thrown.expect(IllegalStateException.class);
    spanStore.getServiceNames();
  }

  @Test
  public void getSpanNames_success() {
    doAnswer(answer(c -> c.onSuccess(asList("span1", "span2"))))
        .when(delegate).getSpanNames(eq("service"), any(Callback.class));

    assertThat(spanStore.getSpanNames("service")).containsExactly("span1", "span2");
  }

  @Test
  public void getSpanNames_exception() {
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(delegate).getSpanNames(eq("service"), any(Callback.class));

    thrown.expect(IllegalStateException.class);
    spanStore.getSpanNames("service");
  }

  @Test
  public void getDependencies_success() {
    doAnswer(answer(c -> c.onSuccess(LINKS)))
        .when(delegate).getDependencies(eq(1L), eq(0L), any(Callback.class));

    assertThat(spanStore.getDependencies(1L, 0L)).containsExactlyElementsOf(LINKS);
  }

  @Test
  public void getDependencies_exception() {
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(delegate).getDependencies(eq(1L), eq(0L), any(Callback.class));

    thrown.expect(IllegalStateException.class);;
    spanStore.getDependencies(1L, 0L);
  }

  static <T> Answer answer(Consumer<Callback<T>> onCallback) {
    return invocation -> {
      onCallback.accept((Callback) invocation.getArguments()[invocation.getArguments().length - 1]);
      return null;
    };
  }
}
