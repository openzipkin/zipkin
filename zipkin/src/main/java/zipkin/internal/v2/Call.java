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
package zipkin.internal.v2;

import java.io.IOException;
import javax.annotation.Nullable;

/**
 * This captures a (usually remote) request and can be used once, either {@link #execute()
 * synchronously} or {@link #enqueue(Callback) asynchronously}. At any time, from any thread, you
 * can call {@linkplain #cancel()}, which might stop an in-flight request or prevent one from
 * occurring.
 *
 * <p>Implementations should prepare a call such that there's little or no likelihood of late
 * runtime exceptions. For example, if the call is to get a trace, the call to {@code listSpans}
 * should propagate input errors vs delay them until a call to {@linkplain #execute()} or
 * {@linkplain #enqueue(Callback)}.
 *
 * <p>Ex.
 * <pre>{@code
 * // Any translation of an input request to remote parameters should happen here, and any related
 * // errors should propagate here.
 * Call<List<List<Span>>> listTraces = spanStore.listTraces(request);
 * // When this executes, it should simply run the remote request.
 * List<Span> trace = getTraceCall.execute();
 * }</pre>
 *
 * <p>An instance of call cannot be invoked more than once, but you can {@linkplain #clone()} an
 * instance if you need to replay the call. There is no relationship between a call and a number of
 * remote requests. For example, an implementation that stores spans may make hundreds of remote
 * requests, possibly retrying on your behalf.
 *
 * <p>This type owes its design to {@code retrofit2.Call}, which is nearly the same, except limited
 * to HTTP transports.
 *
 * @param <V> the success type, only null when {@code V} is {@linkplain Void}.
 */
public abstract class Call<V> implements Cloneable {

  /**
   * Returns a completed call which has the supplied value. This is useful when input parameters
   * imply there's no call needed. For example, an empty input might always result in an empty
   * output.
   */
  public static <V> Call<V> create(@Nullable V v) {
    return new Constant<>(v);
  }

  /**
   * Invokes a request, returning a success value or propagating an error to the caller. Invoking
   * this more than once will result in an error. To repeat a call, make a copy with {@linkplain
   * #clone()}.
   *
   * <p>Eventhough this is a blocking call, implementations may honor calls to {@linkplain
   * #cancel()} from a different thread.
   *
   * @return a success value. Null is unexpected, except when {@code V} is {@linkplain Void}.
   */
  @Nullable public abstract V execute() throws IOException;

  /**
   * Invokes a request asynchronously, signaling the {@code callback} when complete. Invoking this
   * more than once will result in an error. To repeat a call, make a copy with {@linkplain
   * #clone()}.
   */
  public abstract void enqueue(Callback<V> callback);

  /**
   * Requests to cancel this call, even if some implementations may not support it. For example, a
   * blocking call is sometimes not cancelable.
   */
  // Boolean isn't returned because some implementations may cancel asynchronously.
  // Implementing might throw an IOException on execute or callback.onError(IOException)
  public abstract void cancel();

  /**
   * Returns true if {@linkplain #cancel()} was called.
   *
   * <p>Calls can fail before being canceled, so true does always mean cancelation caused a call to
   * fail. That said, successful cancellation does result in a failure.
   */
  // isCanceled exists while isExecuted does not because you do not need the latter to implement
  // asynchronous bindings, such as rxjava2
  public abstract boolean isCanceled();

  /** Returns a copy of this object, so you can make an identical follow-up request. */
  public abstract Call<V> clone();

  static final class Constant<V> extends Call<V> {
    @Nullable final V v;
    volatile boolean canceled;
    boolean executed; // guarded by this

    Constant(@Nullable V v) {
      this.v = v;
    }

    @Override public V execute() throws IOException {
      synchronized (this) {
        if (executed) throw new IllegalStateException("Already Executed");
        executed = true;
      }
      if (canceled) throw new IOException("Canceled");
      return v;
    }

    @Override public void enqueue(Callback<V> callback) {
      synchronized (this) {
        if (executed) throw new IllegalStateException("Already Executed");
        executed = true;
      }
      if (canceled) {
        callback.onError(new IOException("Canceled"));
      } else {
        callback.onSuccess(v);
      }
    }

    @Override public void cancel() {
      canceled = true;
    }

    @Override public boolean isCanceled() {
      return canceled;
    }

    @Override public Call<V> clone() {
      return new Constant<>(v);
    }
  }
}
