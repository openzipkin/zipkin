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
import java.util.concurrent.Executor;
import zipkin.Span;

import static zipkin.internal.Util.checkNotNull;

/**
 * This allows you to build an {@link AsyncSpanConsumer} from a blocking api.
 *
 * <p>In implementation, this runs blocking calls in a thread.
 */
public final class BlockingToAsyncSpanConsumerAdapter implements AsyncSpanConsumer {

  public interface SpanConsumer {
    /** Like {@link AsyncSpanConsumer#accept}, except blocking. Invoked by an executor. */
    void accept(List<Span> spans);
  }

  final SpanConsumer spanConsumer;
  final Executor executor;

  public BlockingToAsyncSpanConsumerAdapter(SpanConsumer spanConsumer, Executor executor) {
    this.spanConsumer = checkNotNull(spanConsumer, "spanConsumer");
    this.executor = checkNotNull(executor, "executor");
  }

  @Override public void accept(final List<Span> spans, Callback<Void> callback) {
    executor.execute(new CallbackRunnable<Void>(callback) {
      @Override Void complete() {
        spanConsumer.accept(spans);
        return null;
      }

      @Override public String toString() {
        return "Accept(" + spans + ")";
      }
    });
  }

  @Override public String toString() {
    return spanConsumer.toString();
  }
}
