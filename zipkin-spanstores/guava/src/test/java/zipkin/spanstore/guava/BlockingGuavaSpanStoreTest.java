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
package zipkin.spanstore.guava;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.QueryRequest;
import zipkin.Span;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class BlockingGuavaSpanStoreTest {

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

  List<Span> trace1 = ImmutableList.of(span1, span2, span3);

  List<Span> trace2 = ImmutableList.of(span4, span5);

  List<List<Span>> traces = ImmutableList.of(trace1, trace2);

  List<DependencyLink> deps = ImmutableList.of(
      new DependencyLink.Builder().parent("zipkin-web").child("zipkin-query").callCount(1).build(),
      new DependencyLink.Builder().parent("zipkin-query").child("zipkin-foo").callCount(10).build()
  );

  @Rule
  public MockitoRule mocks = MockitoJUnit.rule();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock
  private GuavaSpanStore delegate;

  private BlockingGuavaSpanStore spanStore;

  @Before
  public void setUp() {
    spanStore = new BlockingGuavaSpanStore(delegate);
  }

  @Test
  public void getTraces_success() {
    QueryRequest request = new QueryRequest.Builder("service").endTs(1000L).build();
    when(delegate.getTraces(request)).thenReturn(immediateFuture(traces));
    assertThat(spanStore.getTraces(request)).containsExactlyElementsOf(traces);
  }

  @Test
  public void getTraces_exception() {
    QueryRequest request = new QueryRequest.Builder("service").endTs(1000L).build();
    when(delegate.getTraces(request)).thenThrow(new IllegalStateException("failed"));
    thrown.expect(IllegalStateException.class);;
    spanStore.getTraces(request);
  }

  @Test
  public void getTrace_success() {
    when(delegate.getTrace(1L)).thenReturn(immediateFuture(trace1));
    assertThat(spanStore.getTrace(1L)).containsExactlyElementsOf(trace1);
  }

  @Test
  public void getTrace_exception() {
    when(delegate.getTrace(1L)).thenThrow(new IllegalStateException("failed"));
    thrown.expect(IllegalStateException.class);;
    spanStore.getTrace(1L);
  }

  @Test
  public void getRawTrace_success() {
    when(delegate.getRawTrace(1L)).thenReturn(immediateFuture(trace1));
    assertThat(spanStore.getRawTrace(1L)).containsExactlyElementsOf(trace1);
  }

  @Test
  public void getRawTrace_exception() {
    when(delegate.getRawTrace(1L)).thenThrow(new IllegalStateException("failed"));
    thrown.expect(IllegalStateException.class);;
    spanStore.getRawTrace(1L);
  }

  @Test
  public void getServiceNamees_success() {
    when(delegate.getServiceNames())
        .thenReturn(immediateFuture(Arrays.asList("service1", "service2")));
    assertThat(spanStore.getServiceNames()).containsExactly("service1", "service2");
  }

  @Test
  public void getServiceNames_exception() {
    when(delegate.getServiceNames()).thenThrow(new IllegalStateException("failed"));
    thrown.expect(IllegalStateException.class);;
    spanStore.getServiceNames();
  }

  @Test
  public void getSpanNames_success() {
    when(delegate.getSpanNames("service")).thenReturn(immediateFuture(
        Arrays.asList("span1", "span2")));
    assertThat(spanStore.getSpanNames("service")).containsExactly("span1", "span2");
  }

  @Test
  public void getSpanNames_exception() {
    when(delegate.getSpanNames("service")).thenThrow(new IllegalStateException("failed"));
    thrown.expect(IllegalStateException.class);;
    spanStore.getSpanNames("service");
  }

  @Test
  public void getDependencies_success() {
    when(delegate.getDependencies(1L, 0L)).thenReturn(immediateFuture(deps));
    assertThat(spanStore.getDependencies(1L, 0L)).containsExactlyElementsOf(deps);
  }

  @Test
  public void getDependencies_exception() {
    when(delegate.getDependencies(1L, 0L)).thenThrow(new IllegalStateException("failed"));
    thrown.expect(IllegalStateException.class);;
    spanStore.getDependencies(1L, 0L);
  }
}
