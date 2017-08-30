/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.internal;

import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import zipkin.DependencyLink;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.Callback;
import zipkin.storage.QueryRequest;

import static java.util.Arrays.asList;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static zipkin.TestObjects.DAY;
import static zipkin.TestObjects.LINKS;
import static zipkin.TestObjects.LOTS_OF_SPANS;
import static zipkin.TestObjects.TODAY;
import static zipkin.TestObjects.TRACE;

public class LenientDoubleCallbackAsyncSpanStoreTest {

  @Rule public MockitoRule mocks = MockitoJUnit.rule();

  @Mock AsyncSpanStore left;
  @Mock AsyncSpanStore right;
  @Mock Callback callback;

  LenientDoubleCallbackAsyncSpanStore doubleCallbackAsyncSpanStore;
  IllegalStateException leftException = new IllegalStateException("left");
  IllegalStateException rightException = new IllegalStateException("right");

  @Before public void setUp() {
    doubleCallbackAsyncSpanStore = new LenientDoubleCallbackAsyncSpanStore(left, right);
  }

  @Test public void getTraces_merges() throws Exception {
    QueryRequest request = QueryRequest.builder().build();
    doAnswer(answer(c -> c.onSuccess(asList(asList(LOTS_OF_SPANS[0])))))
      .when(left).getTraces(eq(request), any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(asList(asList(LOTS_OF_SPANS[1])))))
      .when(right).getTraces(eq(request), any(Callback.class));

    doubleCallbackAsyncSpanStore.getTraces(request, callback);
    verify(callback).onSuccess(asList(asList(LOTS_OF_SPANS[0]), asList(LOTS_OF_SPANS[1])));
  }

  @Test public void getTraces_okWhenLeftFails() throws Exception {
    QueryRequest request = QueryRequest.builder().build();
    doAnswer(answer(c -> c.onError(leftException)))
      .when(left).getTraces(eq(request), any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(asList(asList(LOTS_OF_SPANS[1])))))
      .when(right).getTraces(eq(request), any(Callback.class));

    doubleCallbackAsyncSpanStore.getTraces(request, callback);
    verify(callback).onSuccess(asList(asList(LOTS_OF_SPANS[1])));
  }

  @Test public void getTraces_okWhenRightFails() throws Exception {
    QueryRequest request = QueryRequest.builder().build();
    doAnswer(answer(c -> c.onSuccess(asList(asList(LOTS_OF_SPANS[0])))))
      .when(left).getTraces(eq(request), any(Callback.class));
    doAnswer(answer(c -> c.onError(rightException)))
      .when(right).getTraces(eq(request), any(Callback.class));

    doubleCallbackAsyncSpanStore.getTraces(request, callback);
    verify(callback).onSuccess(asList(asList(LOTS_OF_SPANS[0])));
  }

  @Test public void getTraces_exceptionWhenBothFail() throws Exception {
    QueryRequest request = QueryRequest.builder().build();
    doAnswer(answer(c -> c.onError(leftException)))
      .when(left).getTraces(eq(request), any(Callback.class));
    doAnswer(answer(c -> c.onError(rightException)))
      .when(right).getTraces(eq(request), any(Callback.class));

    doubleCallbackAsyncSpanStore.getTraces(request, callback);
    verify(callback).onError(rightException);
  }

  @Test public void getTrace_merges() throws Exception {
    doAnswer(answer(c -> c.onSuccess(asList(TRACE.get(0), TRACE.get(1)))))
      .when(left).getTrace(eq(1L), eq(2L), any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(asList(TRACE.get(2)))))
      .when(right).getTrace(eq(1L), eq(2L), any(Callback.class));

    doubleCallbackAsyncSpanStore.getTrace(1L, 2L, callback);
    verify(callback).onSuccess(TRACE);
  }

  @Test public void getTrace_leftIsNull() throws Exception {
    doAnswer(answer(c -> c.onSuccess(null)))
      .when(left).getTrace(eq(1L), eq(2L), any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(asList(TRACE.get(2)))))
      .when(right).getTrace(eq(1L), eq(2L), any(Callback.class));

    doubleCallbackAsyncSpanStore.getTrace(1L, 2L, callback);
    verify(callback).onSuccess(asList(TRACE.get(2)));
  }

  @Test public void getTrace_rightIsNull() throws Exception {
    doAnswer(answer(c -> c.onSuccess(asList(TRACE.get(0), TRACE.get(1)))))
      .when(left).getTrace(eq(1L), eq(2L), any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(null)))
      .when(right).getTrace(eq(1L), eq(2L), any(Callback.class));

    doubleCallbackAsyncSpanStore.getTrace(1L, 2L, callback);
    verify(callback).onSuccess(asList(TRACE.get(0), TRACE.get(1)));
  }

  @Test public void getTrace_okWhenLeftFails() throws Exception {
    doAnswer(answer(c -> c.onError(leftException)))
      .when(left).getTrace(eq(1L), eq(2L), any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(TRACE)))
      .when(right).getTrace(eq(1L), eq(2L), any(Callback.class));

    doubleCallbackAsyncSpanStore.getTrace(1L, 2L, callback);
    verify(callback).onSuccess(TRACE);
  }

  @Test public void getTrace_okWhenLeftFails_rightIsNull() throws Exception {
    doAnswer(answer(c -> c.onError(leftException)))
      .when(left).getTrace(eq(1L), eq(2L), any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(null)))
      .when(right).getTrace(eq(1L), eq(2L), any(Callback.class));

    doubleCallbackAsyncSpanStore.getTrace(1L, 2L, callback);
    verify(callback).onSuccess(null);
  }

  @Test public void getTrace_okWhenRightFails() throws Exception {
    doAnswer(answer(c -> c.onSuccess(TRACE)))
      .when(left).getTrace(eq(1L), eq(2L), any(Callback.class));
    doAnswer(answer(c -> c.onError(rightException)))
      .when(right).getTrace(eq(1L), eq(2L), any(Callback.class));

    doubleCallbackAsyncSpanStore.getTrace(1L, 2L, callback);
    verify(callback).onSuccess(TRACE);
  }

  @Test public void getTrace_okWhenRightFails_leftIsNull() throws Exception {
    doAnswer(answer(c -> c.onSuccess(null)))
      .when(left).getTrace(eq(1L), eq(2L), any(Callback.class));
    doAnswer(answer(c -> c.onError(rightException)))
      .when(right).getTrace(eq(1L), eq(2L), any(Callback.class));

    doubleCallbackAsyncSpanStore.getTrace(1L, 2L, callback);
    verify(callback).onSuccess(null);
  }

  @Test public void getTrace_exceptionWhenBothFail() throws Exception {
    doAnswer(answer(c -> c.onError(leftException)))
      .when(left).getTrace(eq(1L), eq(2L), any(Callback.class));
    doAnswer(answer(c -> c.onError(rightException)))
      .when(right).getTrace(eq(1L), eq(2L), any(Callback.class));

    doubleCallbackAsyncSpanStore.getTrace(1L, 2L, callback);
    verify(callback).onError(rightException);
  }

  @Test public void getRawTrace_merges() throws Exception {
    doAnswer(answer(c -> c.onSuccess(asList(TRACE.get(0), TRACE.get(1)))))
      .when(left).getRawTrace(eq(1L), eq(2L), any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(asList(TRACE.get(2)))))
      .when(right).getRawTrace(eq(1L), eq(2L), any(Callback.class));

    doubleCallbackAsyncSpanStore.getRawTrace(1L, 2L, callback);
    verify(callback).onSuccess(TRACE);
  }

  @Test public void getRawTrace_okWhenLeftFails() throws Exception {
    doAnswer(answer(c -> c.onError(leftException)))
      .when(left).getRawTrace(eq(1L), eq(2L), any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(TRACE)))
      .when(right).getRawTrace(eq(1L), eq(2L), any(Callback.class));

    doubleCallbackAsyncSpanStore.getRawTrace(1L, 2L, callback);
    verify(callback).onSuccess(TRACE);
  }

  @Test public void getRawTrace_okWhenRightFails() throws Exception {
    doAnswer(answer(c -> c.onSuccess(TRACE)))
      .when(left).getRawTrace(eq(1L), eq(2L), any(Callback.class));
    doAnswer(answer(c -> c.onError(rightException)))
      .when(right).getRawTrace(eq(1L), eq(2L), any(Callback.class));

    doubleCallbackAsyncSpanStore.getRawTrace(1L, 2L, callback);
    verify(callback).onSuccess(TRACE);
  }

  @Test public void getRawTrace_exceptionWhenBothFail() throws Exception {
    doAnswer(answer(c -> c.onError(leftException)))
      .when(left).getRawTrace(eq(1L), eq(2L), any(Callback.class));
    doAnswer(answer(c -> c.onError(rightException)))
      .when(right).getRawTrace(eq(1L), eq(2L), any(Callback.class));

    doubleCallbackAsyncSpanStore.getRawTrace(1L, 2L, callback);
    verify(callback).onError(rightException);
  }

  @Test public void getServiceNames_merges() throws Exception {
    doAnswer(answer(c -> c.onSuccess(asList("service1", "service2"))))
      .when(left).getServiceNames(any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(asList("service3"))))
      .when(right).getServiceNames(any(Callback.class));

    doubleCallbackAsyncSpanStore.getServiceNames(callback);
    verify(callback).onSuccess(asList("service1", "service2", "service3"));
  }

  @Test public void getServiceNames_okWhenLeftFails() throws Exception {
    doAnswer(answer(c -> c.onError(leftException)))
      .when(left).getServiceNames(any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(asList("service3"))))
      .when(right).getServiceNames(any(Callback.class));

    doubleCallbackAsyncSpanStore.getServiceNames(callback);
    verify(callback).onSuccess(asList("service3"));
  }

  @Test public void getServiceNames_okWhenRightFails() throws Exception {
    doAnswer(answer(c -> c.onSuccess(asList("service1", "service2"))))
      .when(left).getServiceNames(any(Callback.class));
    doAnswer(answer(c -> c.onError(rightException)))
      .when(right).getServiceNames(any(Callback.class));

    doubleCallbackAsyncSpanStore.getServiceNames(callback);
    verify(callback).onSuccess(asList("service1", "service2"));
  }

  @Test public void getServiceNames_exceptionWhenBothFail() throws Exception {
    doAnswer(answer(c -> c.onError(leftException)))
      .when(left).getServiceNames(any(Callback.class));
    doAnswer(answer(c -> c.onError(rightException)))
      .when(right).getServiceNames(any(Callback.class));

    doubleCallbackAsyncSpanStore.getServiceNames(callback);
    verify(callback).onError(rightException);
  }

  @Test public void getSpanNames_merges() throws Exception {
    doAnswer(answer(c -> c.onSuccess(asList("get", "post"))))
      .when(left).getSpanNames(eq("service"), any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(asList("delete"))))
      .when(right).getSpanNames(eq("service"), any(Callback.class));

    doubleCallbackAsyncSpanStore.getSpanNames("service", callback);
    verify(callback).onSuccess(asList("get", "post", "delete"));
  }

  @Test public void getSpanNames_okWhenLeftFails() throws Exception {
    doAnswer(answer(c -> c.onError(leftException)))
      .when(left).getSpanNames(eq("service"), any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(asList("delete"))))
      .when(right).getSpanNames(eq("service"), any(Callback.class));

    doubleCallbackAsyncSpanStore.getSpanNames("service", callback);
    verify(callback).onSuccess(asList("delete"));
  }

  @Test public void getSpanNames_okWhenRightFails() throws Exception {
    doAnswer(answer(c -> c.onSuccess(asList("get", "post"))))
      .when(left).getSpanNames(eq("service"), any(Callback.class));
    doAnswer(answer(c -> c.onError(rightException)))
      .when(right).getSpanNames(eq("service"), any(Callback.class));

    doubleCallbackAsyncSpanStore.getSpanNames("service", callback);
    verify(callback).onSuccess(asList("get", "post"));
  }

  @Test public void getSpanNames_exceptionWhenBothFail() throws Exception {
    doAnswer(answer(c -> c.onError(leftException)))
      .when(left).getSpanNames(eq("service"), any(Callback.class));
    doAnswer(answer(c -> c.onError(rightException)))
      .when(right).getSpanNames(eq("service"), any(Callback.class));

    doubleCallbackAsyncSpanStore.getSpanNames("service", callback);
    verify(callback).onError(rightException);
  }

  @Test public void getDependencies_merges() throws Exception {
    doAnswer(answer(c -> c.onSuccess(asList(
      DependencyLink.builder().parent("web").child("app").callCount(1L).build(),
      DependencyLink.builder().parent("app").child("db").callCount(1L).errorCount(1L).build()
    ))))
      .when(left).getDependencies(eq(TODAY), eq(DAY), any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(asList(
      DependencyLink.builder().parent("web").child("app").callCount(2L).errorCount(1L).build()
    ))))
      .when(right).getDependencies(eq(TODAY), eq(DAY), any(Callback.class));

    doubleCallbackAsyncSpanStore.getDependencies(TODAY, DAY, callback);
    verify(callback).onSuccess(asList(
      DependencyLink.builder().parent("web").child("app").callCount(3L).errorCount(1L).build(),
      DependencyLink.builder().parent("app").child("db").callCount(1L).errorCount(1L).build()
    ));
  }

  @Test public void getDependencies_okWhenLeftFails() throws Exception {
    doAnswer(answer(c -> c.onError(leftException)))
      .when(left).getDependencies(eq(TODAY), eq(DAY), any(Callback.class));
    doAnswer(answer(c -> c.onSuccess(LINKS)))
      .when(right).getDependencies(eq(TODAY), eq(DAY), any(Callback.class));

    doubleCallbackAsyncSpanStore.getDependencies(TODAY, DAY, callback);
    verify(callback).onSuccess(LINKS);
  }

  @Test public void getDependencies_okWhenRightFails() throws Exception {
    doAnswer(answer(c -> c.onSuccess(LINKS)))
      .when(left).getDependencies(eq(TODAY), eq(DAY), any(Callback.class));
    doAnswer(answer(c -> c.onError(rightException)))
      .when(right).getDependencies(eq(TODAY), eq(DAY), any(Callback.class));

    doubleCallbackAsyncSpanStore.getDependencies(TODAY, DAY, callback);
    verify(callback).onSuccess(LINKS);
  }

  @Test public void getDependencies_exceptionWhenBothFail() throws Exception {
    doAnswer(answer(c -> c.onError(leftException)))
      .when(left).getDependencies(eq(TODAY), eq(DAY), any(Callback.class));
    doAnswer(answer(c -> c.onError(rightException)))
      .when(right).getDependencies(eq(TODAY), eq(DAY), any(Callback.class));

    doubleCallbackAsyncSpanStore.getDependencies(TODAY, DAY, callback);
    verify(callback).onError(rightException);
  }

  static <T> Answer answer(Consumer<Callback<T>> onCallback) {
    return invocation -> {
      onCallback.accept((Callback) invocation.getArguments()[invocation.getArguments().length - 1]);
      return null;
    };
  }
}
