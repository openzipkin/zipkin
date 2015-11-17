/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin;

import java.util.List;
import org.junit.Before;
import org.junit.Test;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base test for {@link SpanStore} implementations. Subtypes should create a connection to a real
 * backend, even if that backend is in-process.
 *
 * <p/>Incrementally, this will replace {@code com.twitter.zipkin.storage.SpanStoreSpec}.
 */
public abstract class SpanStoreTest<T extends SpanStore> {

  /** Should maintain state between multiple calls within a test. */
  protected final T store;

  protected SpanStoreTest(T store) {
    this.store = store;
  }

  /** Clears the span store between tests. */
  @Before
  public abstract void clear();

  Endpoint ep = Endpoint.create("service", 127 << 24 | 1, 8080);

  long spanId = 456;
  Annotation ann1 = Annotation.create(1000L, "cs", ep);
  Annotation ann2 = Annotation.create(2000L, "sr", null);
  Annotation ann3 = Annotation.create(10000L, "custom", ep);
  Annotation ann4 = Annotation.create(20000L, "custom", ep);
  Annotation ann5 = Annotation.create(5000L, "custom", ep);
  Annotation ann6 = Annotation.create(6000L, "custom", ep);
  Annotation ann7 = Annotation.create(7000L, "custom", ep);
  Annotation ann8 = Annotation.create(8000L, "custom", ep);

  Span span1 = new Span.Builder()
      .traceId(123)
      .name("methodcall")
      .id(spanId)
      .addAnnotation(ann1)
      .addAnnotation(ann3)
      .addBinaryAnnotation(BinaryAnnotation.create("BAH", "BEH", ep)).build();

  Span span2 = new Span.Builder()
      .traceId(456)
      .name("methodcall")
      .id(spanId)
      .timestamp(2L)
      .addAnnotation(ann2)
      .addBinaryAnnotation(BinaryAnnotation.create("BAH2", "BEH2", ep)).build();

  Span span3 = new Span.Builder()
      .traceId(789)
      .name("methodcall")
      .id(spanId)
      .addAnnotation(ann2)
      .addAnnotation(ann3)
      .addAnnotation(ann4)
      .addBinaryAnnotation(BinaryAnnotation.create("BAH2", "BEH2", ep)).build();

  Span span4 = new Span.Builder()
      .traceId(999)
      .name("methodcall")
      .id(spanId)
      .addAnnotation(ann6)
      .addAnnotation(ann7).build();

  Span span5 = new Span.Builder()
      .traceId(999)
      .name("methodcall")
      .id(spanId)
      .addAnnotation(ann5)
      .addAnnotation(ann8)
      .addBinaryAnnotation(BinaryAnnotation.create("BAH2", "BEH2", ep)).build();

  Span spanEmptySpanName = new Span.Builder()
      .traceId(123)
      .name("")
      .id(spanId)
      .parentId(1L)
      .addAnnotation(ann1)
      .addAnnotation(ann2).build();

  Span spanEmptyServiceName = new Span.Builder()
      .traceId(123)
      .name("spanname")
      .id(spanId).build();

  Span mergedSpan = new Span.Builder()
      .traceId(123)
      .name("methodcall")
      .id(spanId)
      .addAnnotation(ann1)
      .addAnnotation(ann2)
      .addBinaryAnnotation(BinaryAnnotation.create("BAH2", "BEH2", ep)).build();

  /**
   * Basic clock skew correction is something span stores should support, until the UI supports
   * happens-before without using timestamps. The easiest clock skew to correct is where a child
   * appears to happen before the parent.
   *
   * <p/>It doesn't matter if clock-skew correction happens at storage or query time, as long as it
   * occurs by the time results are returned.
   *
   * <p/>Span stores who don't support this can override and disable this test, noting in the README
   * the limitation.
   */
  @Test
  public void correctsClockSkew() {
    Endpoint client = Endpoint.create("client", 192 << 24 | 168 << 16 | 1, 8080);
    Endpoint frontend = Endpoint.create("frontend", 192 << 24 | 168 << 16 | 2, 8080);
    Endpoint backend = Endpoint.create("backend", 192 << 24 | 168 << 16 | 3, 8080);

    Span parent = new Span.Builder()
        .traceId(1)
        .name("method1")
        .id(666)
        .addAnnotation(Annotation.create(100000, Constants.CLIENT_SEND, client))
        .addAnnotation(Annotation.create(95000, Constants.SERVER_RECV, frontend)) // before client sends
        .addAnnotation(Annotation.create(120000, Constants.SERVER_SEND, frontend)) // before client receives
        .addAnnotation(Annotation.create(135000, Constants.CLIENT_RECV, client)).build();

    Span child = new Span.Builder()
        .traceId(1)
        .name("method2")
        .id(777)
        .parentId(666L)
        .addAnnotation(Annotation.create(100000, Constants.CLIENT_SEND, frontend))
        .addAnnotation(Annotation.create(115000, Constants.SERVER_RECV, backend))
        .addAnnotation(Annotation.create(120000, Constants.SERVER_SEND, backend))
        .addAnnotation(Annotation.create(115000, Constants.CLIENT_RECV, frontend)) // before server sent
        .build();

    List<Span> skewed = asList(parent, child);

    // There's clock skew when the child doesn't happen after the parent
    assertThat(skewed.get(0).timestamp)
        .isLessThanOrEqualTo(skewed.get(1).timestamp);

    // Regardless of when clock skew is corrected, it should be corrected before traces return
    store.accept(asList(parent, child));
    List<Span> adjusted = store.getTracesByIds(asList(1L)).get(0);

    // After correction, the child happens after the parent
    assertThat(adjusted.get(0).timestamp)
        .isLessThanOrEqualTo(adjusted.get(1).timestamp);

    // .. because the child is shifted to a later date
    assertThat(adjusted.get(1).timestamp)
        .isGreaterThan(skewed.get(1).timestamp);

    // Since we've shifted the child to a later timestamp, the total duration appears shorter
    assertThat(adjusted.get(0).duration)
        .isLessThan(skewed.get(0).duration);

    // .. but that change in duration should be accounted for
    long shift = adjusted.get(0).timestamp - skewed.get(0).timestamp;
    assertThat(adjusted.get(0).duration)
        .isEqualTo(skewed.get(0).duration - shift);
  }
}
