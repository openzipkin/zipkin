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
import zipkin.internal.Nullable;

/**
 * Spans are created in instrumentation, transported out-of-band, and eventually persisted. A span
 * consumer is a stage along that pipeline. A common consumption case in zipkin is to write spans to
 * storage after applying sampling policy.
 *
 * <p>This accepts a {@link Callback<Void>} to allow bridging to async libraries.
 */
// @FunctionalInterface
public interface AsyncSpanConsumer {
  Callback<Void> NOOP_CALLBACK = new Callback<Void>() {
    @Override public void onSuccess(@Nullable Void value) {
    }

    @Override public void onError(Throwable t) {
    }
  };

  /**
   * Receives a list of spans {@link Codec#readSpans(byte[]) decoded} from a transport.
   *
   * @param spans may be subject to a {@link Sampler#isSampled(long) sampling policy}.
   */
  void accept(List<Span> spans, Callback<Void> callback);
}
