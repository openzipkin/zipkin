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
package zipkin;

import java.util.List;

/**
 * Spans are created in instrumentation, transported out-of-band, and eventually persisted. A span
 * consumer is a stage along that pipeline. A common consumption case in zipkin is to write spans to
 * storage after applying sampling policy.
 */
// @FunctionalInterface
public interface SpanConsumer {

  /**
   * Receives a list of spans {@link Codec#readSpans(byte[]) decoded} from a transport. Usually,
   * this is an asynchronous {@link SpanStore#accept store} command.
   *
   * @param spans may be subject to a {@link Sampler#isSampled(long) sampling policy}.
   */
  void accept(List<Span> spans);
}
