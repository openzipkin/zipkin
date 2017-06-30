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
package zipkin.storage;

import org.junit.Before;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.internal.Util;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class InMemorySpanStoreEvictionTest {
  final InMemoryStorage storage = new InMemoryStorage();
  final InMemorySpanStore store = storage.spanStore();
  StorageAdapters.SpanConsumer consumer = store.spanConsumer;

  @Before
  public void clear() {
    storage.clear();
  }

  long today = Util.midnightUTC(System.currentTimeMillis());

  Endpoint epA = Endpoint.create("serviceA", 127 << 24 | 1);
  Endpoint epB = Endpoint.create("serviceB", 127 << 24 | 1);

  Annotation ann1 = Annotation.create((today + 1) * 1000, "sr", epA);
  Annotation ann2 = Annotation.create((today + 2) * 1000, "ss", epA);
  Annotation ann3 = Annotation.create((today + 3) * 1000, "sr", epA);
  Annotation ann4 = Annotation.create((today + 4) * 1000, "ss", epA);
  Annotation ann5 = Annotation.create((today + 5) * 1000, "sr", epA);
  Annotation ann6 = Annotation.create((today + 6) * 1000, "ss", epA);
  Annotation ann7 = Annotation.create((today + 7) * 1000, "sr", epB);
  Annotation ann8 = Annotation.create((today + 8) * 1000, "ss", epB);

  Span span1 = Span.builder()
    .traceId(0x123)
    .name("GET")
    .timestamp(ann1.timestamp)
    .duration(ann2.timestamp - ann1.timestamp)
    .id(0x987)
    .annotations(asList(ann1, ann2)).build();

  Span span2 = Span.builder()
    .traceId(0x234)
    .name("GET")
    .timestamp(ann3.timestamp)
    .duration(ann4.timestamp - ann3.timestamp)
    .id(0x876)
    .annotations(asList(ann3, ann4)).build();

  Span span3a = Span.builder()
    .traceId(0x345)
    .name("POST")
    .timestamp(ann5.timestamp)
    .duration(ann6.timestamp - ann5.timestamp)
    .id(0x765)
    .annotations(asList(ann5, ann6)).build();

  Span span3b = Span.builder()
    .traceId(0x345)
    .name("GET")
    .timestamp(ann7.timestamp)
    .duration(ann8.timestamp - ann7.timestamp)
    .id(0x654)
    .annotations(asList(ann7, ann8)).build();

  @Test
  public void evict_basic() {
    consumer.accept(asList(span1));
    assertThat(store.getTraces(QueryRequest.builder().build()))
      .containsOnly(asList(span1));

    int spansEvicted = store.evictToRecoverSpans(1);
    assertThat(spansEvicted).isEqualTo(1);
    assertThat(store.getTraces(QueryRequest.builder().build())).isEmpty();
  }

  @Test
  public void evict_detailed() {
    consumer.accept(asList(span1, span2, span3a, span3b));
    assertThat(store.getTraces(QueryRequest.builder().build()))
      .containsOnly(asList(span1), asList(span2), asList(span3a, span3b));
    assertThat(store.getServiceNames()).containsExactly("servicea", "serviceb");

    // Bad parameter -1
    int spansEvicted = store.evictToRecoverSpans(-1);
    assertThat(spansEvicted).isEqualTo(0);
    assertThat(store.getTraces(QueryRequest.builder().build()))
      .containsOnly(asList(span1), asList(span2), asList(span3a, span3b));
    assertThat(store.getServiceNames()).containsExactly("servicea", "serviceb");

    // Nothing to be evict
    spansEvicted = store.evictToRecoverSpans(0);
    assertThat(spansEvicted).isEqualTo(0);
    assertThat(store.getTraces(QueryRequest.builder().build()))
      .containsOnly(asList(span1), asList(span2), asList(span3a, span3b));
    assertThat(store.getServiceNames()).containsExactly("servicea", "serviceb");

    // Evict one span
    spansEvicted = store.evictToRecoverSpans(1);
    assertThat(spansEvicted).isEqualTo(1);
    assertThat(store.getTraces(QueryRequest.builder().build()))
      .containsOnly(asList(span2), asList(span3a, span3b));
    assertThat(store.getServiceNames()).containsExactly("servicea", "serviceb");

    // Evict one more span
    spansEvicted = store.evictToRecoverSpans(1);
    assertThat(spansEvicted).isEqualTo(1);
    assertThat(store.getTraces(QueryRequest.builder().build()))
      .containsOnly(asList(span3a, span3b));
    assertThat(store.getServiceNames()).containsExactly("servicea", "serviceb");

    // Evict more than one at the same time
    spansEvicted = store.evictToRecoverSpans(2);
    assertThat(spansEvicted).isEqualTo(2);
    assertThat(store.getTraces(QueryRequest.builder().build())).isEmpty();
    assertThat(store.getServiceNames()).isEmpty();

    // Call again to check empty case
    spansEvicted = store.evictToRecoverSpans(0);
    assertThat(spansEvicted).isEqualTo(0);
  }

  @Test
  public void evict_oneTraceMultipleSpans() {
    Span testSpan1 = span1.toBuilder().traceIdHigh(1L).traceId(123).
      timestamp(ann1.timestamp).build();
    Span testSpan2 = span2.toBuilder().traceIdHigh(2L).traceId(123).
      timestamp(ann2.timestamp).build();

    consumer.accept(asList(testSpan1, testSpan2));
    assertThat(store.getTraces(QueryRequest.builder().build()))
      .containsOnly(asList(testSpan1), asList(testSpan2));

    // Since both spans are a part of the same trace, getting down to
    // only one span means deleting both (the whole trace)
    int spansEvicted = store.evictToRecoverSpans(1);
    assertThat(spansEvicted).isEqualTo(2);
    assertThat(store.getTraces(QueryRequest.builder().build())).isEmpty();
  }
}
