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
package zipkin2;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
 * @param <V> the success type, typically not null except when {@code V} is {@linkplain Void}.
 */
public abstract class Call<V> implements Cloneable {
  /**
   * Returns a completed call which has the supplied value. This is useful when input parameters
   * imply there's no call needed. For example, an empty input might always result in an empty
   * output.
   */
  public static <V> Call<V> create(V v) {
    return new Constant<>(v);
  }

  @SuppressWarnings("unchecked")
  public static <T> Call<List<T>> emptyList() {
    return Call.create(Collections.emptyList());
  }

  public interface Mapper<V1, V2> {
    V2 map(V1 input);
  }

  /**
   * Maps the result of this call into a different shape, as defined by the {@code mapper} function.
   * This is used to convert values from one type to another. For example, you could use this to
   * convert between zipkin v1 and v2 span format.
   *
   * <pre>{@code
   * getTracesV1Call = getTracesV2Call.map(traces -> v2TracesConverter);
   * }</pre>
   *
   * <p>This method intends to be used for chaining. That means "this" instance should be discarded
   * in favor of the result of this method.
   */
  public final <R> Call<R> map(Mapper<V, R> mapper) {
    return new Mapping<>(mapper, this);
  }

  public interface FlatMapper<V1, V2> {
    Call<V2> map(V1 input);
  }

  /**
   * Maps the result of this call into another, as defined by the {@code flatMapper} function. This
   * is used to chain two remote calls together. For example, you could use this to chain a list IDs
   * call to a get by IDs call.
   *
   * <pre>{@code
   * getTracesCall = getIdsCall.flatMap(ids -> getTraces(ids));
   *
   * // this would now invoke the chain
   * traces = getTracesCall.enqueue(tracesCallback);
   * }</pre>
   *
   * Cancelation propagates to the mapped call.
   *
   * <p>This method intends to be used for chaining. That means "this" instance should be discarded
   * in favor of the result of this method.
   */
  public final <R> Call<R> flatMap(FlatMapper<V, R> flatMapper) {
    return new FlatMapping<>(flatMapper, this);
  }

  public interface ErrorHandler<V> {
    /** Attempts to resolve an error. The user must call the callback. */
    void onErrorReturn(Throwable error, Callback<V> callback);
  }

  /**
   * Returns a call which can attempt to resolve an exception. This is useful when a remote call
   * returns an error when a resource is not found.
   *
   * <p>Here's an example of coercing 404 to empty:
   * <pre>{@code
   * call.handleError((error, callback) -> {
   *   if (error instanceof HttpException && ((HttpException) error).code == 404) {
   *     callback.onSuccess(Collections.emptyList());
   *   } else {
   *     callback.onError(error);
   *   }
   * });
   * }</pre>
   */
  public final Call<V> handleError(ErrorHandler<V> errorHandler) {
    return new ErrorHandling<>(errorHandler, this);
  }

  // Taken from RxJava throwIfFatal, which was taken from scala
  public static void propagateIfFatal(Throwable t) {
    if (t instanceof VirtualMachineError) {
      throw (VirtualMachineError) t;
    } else if (t instanceof ThreadDeath) {
      throw (ThreadDeath) t;
    } else if (t instanceof LinkageError) {
      throw (LinkageError) t;
    }
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
  public abstract V execute() throws IOException;

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
  @Override public abstract Call<V> clone();

  static class Constant<V> extends Base<V> { // not final for mock testing
    final V v;

    Constant(V v) {
      this.v = v;
    }

    @Override protected V doExecute() {
      return v;
    }

    @Override protected void doEnqueue(Callback<V> callback) {
      callback.onSuccess(v);
    }

    @Override public Call<V> clone() {
      return new Constant<>(v);
    }

    @Override public String toString() {
      return "ConstantCall{value=" + v + "}";
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (o instanceof Constant) {
        Constant that = (Constant) o;
        return ((this.v == null) ? (that.v == null) : this.v.equals(that.v));
      }
      return false;
    }

    @Override
    public int hashCode() {
      int h = 1;
      h *= 1000003;
      h ^= (v == null) ? 0 : v.hashCode();
      return h;
    }
  }

  static final class Mapping<R, V> extends Base<R> {
    final Mapper<V, R> mapper;
    final Call<V> delegate;

    Mapping(Mapper<V, R> mapper, Call<V> delegate) {
      this.mapper = mapper;
      this.delegate = delegate;
    }

    @Override protected R doExecute() throws IOException {
      return mapper.map(delegate.execute());
    }

    @Override protected void doEnqueue(final Callback<R> callback) {
      delegate.enqueue(new Callback<V>() {
        @Override public void onSuccess(V value) {
          try {
            callback.onSuccess(mapper.map(value));
          } catch (Throwable t) {
            callback.onError(t);
          }
        }

        @Override public void onError(Throwable t) {
          callback.onError(t);
        }
      });
    }

    @Override public String toString() {
      return "Mapping{call=" + delegate + ", mapper=" + mapper + "}";
    }

    @Override public Call<R> clone() {
      return new Mapping<>(mapper, delegate.clone());
    }
  }

  static final class FlatMapping<R, V> extends Base<R> {
    final FlatMapper<V, R> flatMapper;
    final Call<V> delegate;
    volatile Call<R> mapped;

    FlatMapping(FlatMapper<V, R> flatMapper, Call<V> delegate) {
      this.flatMapper = flatMapper;
      this.delegate = delegate;
    }

    @Override protected R doExecute() throws IOException {
      return (mapped = flatMapper.map(delegate.execute())).execute();
    }

    @Override protected void doEnqueue(final Callback<R> callback) {
      delegate.enqueue(new Callback<V>() {
        @Override public void onSuccess(V value) {
          try {
            (mapped = flatMapper.map(value)).enqueue(callback);
          } catch (Throwable t) {
            propagateIfFatal(t);
            callback.onError(t);
          }
        }

        @Override public void onError(Throwable t) {
          callback.onError(t);
        }
      });
    }

    @Override protected void doCancel() {
      delegate.cancel();
      if (mapped != null) mapped.cancel();
    }

    @Override public String toString() {
      return "FlatMapping{call=" + delegate + ", flatMapper=" + flatMapper + "}";
    }

    @Override public Call<R> clone() {
      return new FlatMapping<>(flatMapper, delegate.clone());
    }
  }

  static final class ErrorHandling<V> extends Base<V> {
    static final Object SENTINEL = new Object(); // to differentiate from null
    final ErrorHandler<V> errorHandler;
    final Call<V> delegate;

    ErrorHandling(ErrorHandler<V> errorHandler, Call<V> delegate) {
      this.errorHandler = errorHandler;
      this.delegate = delegate;
    }

    @Override protected V doExecute() throws IOException {
      try {
        return delegate.execute();
      } catch (IOException | RuntimeException | Error e) {
        final AtomicReference ref = new AtomicReference<>(SENTINEL);
        errorHandler.onErrorReturn(e, new Callback<V>() {
          @Override public void onSuccess(V value) {
            ref.set(value);
          }

          @Override public void onError(Throwable t) {
          }
        });
        Object result = ref.get();
        if (SENTINEL == result) throw e;
        return (V) result;
      }
    }

    @Override protected void doEnqueue(final Callback<V> callback) {
      delegate.enqueue(new Callback<V>() {
        @Override public void onSuccess(V value) {
          callback.onSuccess(value);
        }

        @Override public void onError(Throwable t) {
          errorHandler.onErrorReturn(t, callback);
        }
      });
    }

    @Override protected void doCancel() {
      delegate.cancel();
    }

    @Override public String toString() {
      return "ErrorHandling{call=" + delegate + ", errorHandler=" + errorHandler + "}";
    }

    @Override public Call<V> clone() {
      return new ErrorHandling<>(errorHandler, delegate.clone());
    }
  }

  public static abstract class Base<V> extends Call<V> {
    volatile boolean canceled;
    boolean executed;

    protected Base() {
    }

    @Override public final V execute() throws IOException {
      synchronized (this) {
        if (this.executed) throw new IllegalStateException("Already Executed");
        this.executed = true;
      }

      if (isCanceled()) {
        throw new IOException("Canceled");
      } else {
        return this.doExecute();
      }
    }

    protected abstract V doExecute() throws IOException;

    @Override public final void enqueue(Callback<V> callback) {
      synchronized (this) {
        if (this.executed) throw new IllegalStateException("Already Executed");
        this.executed = true;
      }

      if (isCanceled()) {
        callback.onError(new IOException("Canceled"));
      } else {
        this.doEnqueue(callback);
      }
    }

    protected abstract void doEnqueue(Callback<V> callback);

    @Override public final void cancel() {
      this.canceled = true;
      doCancel();
    }

    protected void doCancel() {
    }

    @Override public final boolean isCanceled() {
      return this.canceled || doIsCanceled();
    }

    protected boolean doIsCanceled() {
      return false;
    }
  }
}
