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
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.QueryRequest;
import zipkin.Span;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;

public class BlockingSpanStoreAdapterTest {

  long spanId = 456;
  long today = System.currentTimeMillis();
  Endpoint ep = Endpoint.create("service", 127 << 24 | 1, 8080);

  Annotation ann1 = Annotation.create((today + 1) * 1000, "cs", ep);
  Annotation ann2 = Annotation.create((today + 2) * 1000, "sr", null);
  Annotation ann3 = Annotation.create((today + 10) * 1000, "custom", ep);
  Annotation ann4 = Annotation.create((today + 20) * 1000, "custom", ep);
  Annotation ann5 = Annotation.create((today + 5) * 1000, "custom", ep);
  Annotation ann6 = Annotation.create((today + 6) * 1000, "custom", ep);
  Annotation ann7 = Annotation.create((today + 7) * 1000, "custom", ep);
  Annotation ann8 = Annotation.create((today + 8) * 1000, "custom", ep);

  Span span1 = new Span.Builder()
      .traceId(123)
      .name("methodcall")
      .id(spanId)
      .timestamp(ann1.timestamp).duration(9000L)
      .annotations(asList(ann1, ann3))
      .addBinaryAnnotation(BinaryAnnotation.create("BAH", "BEH", ep)).build();

  Span span2 = new Span.Builder()
      .traceId(456)
      .name("methodcall")
      .id(spanId)
      .timestamp(ann2.timestamp)
      .addAnnotation(ann2)
      .addBinaryAnnotation(BinaryAnnotation.create("BAH2", "BEH2", ep)).build();

  Span span3 = new Span.Builder()
      .traceId(789)
      .name("methodcall")
      .id(spanId)
      .timestamp(ann2.timestamp).duration(18000L)
      .annotations(asList(ann2, ann3, ann4))
      .addBinaryAnnotation(BinaryAnnotation.create("BAH2", "BEH2", ep)).build();

  Span span4 = new Span.Builder()
      .traceId(999)
      .name("methodcall")
      .id(spanId)
      .timestamp(ann6.timestamp).duration(1000L)
      .annotations(asList(ann6, ann7)).build();

  Span span5 = new Span.Builder()
      .traceId(999)
      .name("methodcall")
      .id(spanId)
      .timestamp(ann5.timestamp).duration(3000L)
      .annotations(asList(ann5, ann8))
      .addBinaryAnnotation(BinaryAnnotation.create("BAH2", "BEH2", ep)).build();

  List<Span> trace1 = asList(span1, span2, span3);

  List<Span> trace2 = asList(span4, span5);

  List<List<Span>> traces = asList(trace1, trace2);

  List<DependencyLink> deps = asList(
      new DependencyLink.Builder().parent("zipkin-web").child("zipkin-query").callCount(1).build(),
      new DependencyLink.Builder().parent("zipkin-query").child("zipkin-foo").callCount(10).build()
  );

  @Rule
  public MockitoRule mocks = MockitoJUnit.rule();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock
  private AsyncSpanStore delegate;

  private BlockingSpanStoreAdapter spanStore;

  @Before
  public void setUp() {
    spanStore = new BlockingSpanStoreAdapter(delegate);
  }

  @Test
  public void getTraces_success() {
    QueryRequest request = new QueryRequest.Builder("service").endTs(1000L).build();
    doAnswer(answer(c -> c.onSuccess(traces)))
        .when(delegate).getTraces(eq(request), any(Callback.class));

    assertThat(spanStore.getTraces(request)).containsExactlyElementsOf(traces);
  }


  @Test
  public void getTraces_exception() {
    QueryRequest request = new QueryRequest.Builder("service").endTs(1000L).build();
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(delegate).getTraces(eq(request), any(Callback.class));

    thrown.expect(IllegalStateException.class);
    spanStore.getTraces(request);
  }

  @Test
  public void getTrace_success() {
    doAnswer(answer(c -> c.onSuccess(trace1)))
        .when(delegate).getTrace(eq(1L), any(Callback.class));

    assertThat(spanStore.getTrace(1L)).containsExactlyElementsOf(trace1);
  }

  @Test
  public void getTrace_exception() {
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(delegate).getTrace(eq(1L), any(Callback.class));

    thrown.expect(IllegalStateException.class);
    spanStore.getTrace(1L);
  }

  @Test
  public void getRawTrace_success() {
    doAnswer(answer(c -> c.onSuccess(trace1)))
        .when(delegate).getRawTrace(eq(1L), any(Callback.class));

    assertThat(spanStore.getRawTrace(1L)).containsExactlyElementsOf(trace1);
  }

  @Test
  public void getRawTrace_exception() {
    doAnswer(answer(c -> c.onError(new IllegalStateException("failed"))))
        .when(delegate).getRawTrace(eq(1L), any(Callback.class));

    thrown.expect(IllegalStateException.class);;
    spanStore.getRawTrace(1L);
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
    doAnswer(answer(c -> c.onSuccess(deps)))
        .when(delegate).getDependencies(eq(1L), eq(0L), any(Callback.class));

    assertThat(spanStore.getDependencies(1L, 0L)).containsExactlyElementsOf(deps);
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
