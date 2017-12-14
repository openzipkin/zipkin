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

import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import zipkin.Annotation;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.TestObjects;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.TODAY;

public class InMemorySpanStoreTest {
  InMemoryStorage storage = new InMemoryStorage.Builder().maxSpanCount(1000).build();
  InMemorySpanStore store = storage.spanStore();
  StorageAdapters.SpanConsumer consumer = store.spanConsumer;

  @Before
  public void clear() {
    storage.clear();
  }

  Endpoint epA = Endpoint.create("serviceA", 127 << 24 | 1);
  Endpoint epB = Endpoint.create("serviceB", 127 << 24 | 1);

  Annotation ann1 = Annotation.create((TODAY + 1) * 1000, "sr", epA);
  Annotation ann2 = Annotation.create((TODAY + 2) * 1000, "ss", epA);
  Annotation ann3 = Annotation.create((TODAY + 3) * 1000, "sr", epA);
  Annotation ann4 = Annotation.create((TODAY + 4) * 1000, "ss", epA);
  Annotation ann5 = Annotation.create((TODAY + 5) * 1000, "sr", epA);
  Annotation ann6 = Annotation.create((TODAY + 6) * 1000, "ss", epA);
  Annotation ann7 = Annotation.create((TODAY + 7) * 1000, "sr", epB);
  Annotation ann8 = Annotation.create((TODAY + 8) * 1000, "ss", epB);

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

  /** Ensures we don't overload a partition due to key equality being conflated with order */
  @Test
  public void differentiatesOnTraceIdWhenTimestampEqual() {
    consumer.accept(asList(span1));
    consumer.accept(asList(span1.toBuilder().traceId(333L).build()));

    assertThat(store).extracting("spansByTraceIdTimeStamp.delegate")
      .allSatisfy(map -> assertThat((Map) map).hasSize(2));
  }

  @Test
  public void dropsLargerThanMax() {
    consumer.accept(asList(TestObjects.LOTS_OF_SPANS));
    assertThat(store.acceptedSpanCount)
      .isEqualTo(store.maxSpanCount);
  }

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
    Span testSpan1 = span1;
    Span testSpan2 = span2.toBuilder().traceId(span1.traceId).build();

    consumer.accept(asList(testSpan1, testSpan2));
    assertThat(store.getTraces(QueryRequest.builder().build()))
      .containsOnly(asList(testSpan1, testSpan2));

    // Since both spans are a part of the same trace, getting down to
    // only one span means deleting both (the whole trace)
    int spansEvicted = store.evictToRecoverSpans(1);
    assertThat(spansEvicted).isEqualTo(2);
    assertThat(store.getTraces(QueryRequest.builder().build())).isEmpty();
  }

  /**
   * This test does not use the default InMemoryStorage used by other tests
   * because it needs one with a slightly different configuation.
   *
   * <p>The purpose of this test it to ensure that the maxSpans setting is respected.
   */
  @Test
  public void acceptAndEvict() {
    InMemoryStorage storageWith2MaxSpans = InMemoryStorage.builder().maxSpanCount(2).build();
    assertThat(storageWith2MaxSpans.spanStore().getTraces(QueryRequest.builder().build()))
      .isEmpty();

    Span testSpan1 = span1.toBuilder().traceIdHigh(1L).build();
    storageWith2MaxSpans.spanConsumer().accept(asList(testSpan1));
    assertThat(storageWith2MaxSpans.spanStore().getTraces(QueryRequest.builder().build()))
      .containsOnly(asList(testSpan1));

    Span testSpan2 = span2.toBuilder().traceIdHigh(2L).build();
    storageWith2MaxSpans.spanConsumer().accept(asList(testSpan2));
    assertThat(storageWith2MaxSpans.spanStore().getTraces(QueryRequest.builder().build()))
      .containsOnly(asList(testSpan1), asList(testSpan2));

    // After adding third span, first will be evicted
    Span testSpan3 = span3a.toBuilder().traceIdHigh(3L).build();
    storageWith2MaxSpans.spanConsumer().accept(asList(testSpan3));
    assertThat(storageWith2MaxSpans.spanStore().getTraces(QueryRequest.builder().build()))
      .containsOnly(asList(testSpan2), asList(testSpan3));
  }
}
