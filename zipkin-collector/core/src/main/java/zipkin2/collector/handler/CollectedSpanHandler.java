/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.collector.handler;

import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.internal.Nullable;

/**
 * Triggered on each collected span, before storage. This allows the ability to mutate or drop spans
 * for reasons including remapping tags.
 *
 * <p>This design is a near copy of brave.handler.FinishedSpanHandler
 *
 * @since 2.17
 */
public interface CollectedSpanHandler { // Java 1.8+ so default methods ok
  /** Use to avoid comparing against null references */
  CollectedSpanHandler NOOP = new CollectedSpanHandler() {
    @Override public Span handle(Span span) {
      return span;
    }

    @Override public String toString() {
      return "NoopCollectedSpanHandler{}";
    }
  };

  /**
   * This is invoked after a span is collected, allowing data to be modified or reported out of
   * process. A return value of null means the span should be dropped completely from the stream.
   *
   * <p>Changes to the input span are visible by later collected span handlers. One reason to
   * change the input is to align tags, so that correlation occurs. For example, some may clean the
   * tag "http.path" knowing downstream handlers such as zipkin reporting have the same value.
   *
   * <p>Returning null is the same effect as if it was dropped upstream. Implementations should be
   * careful when returning false as it can lead to broken traces. Acceptable use cases are when the
   * span is a leaf, for example a client call to an uninstrumented database, or a server call which
   * is known to terminate in-process (for example, health-checks). Prefer an instrumentation policy
   * approach to this mechanism as it results in less overhead.
   *
   * <p>It is ok to handle spans in a way that incurs no transformation, such as aggregating
   * metrics. However it is inappropriate to raise any kind of exception from the handle method.
   * Please code defensively.
   *
   * @param span model as {@link SpanBytesDecoder decoded} or handled upstream.
   * @return the input if unmodified, a derived value, or null to drop this span.
   */
  @Nullable Span handle(Span span);
}
