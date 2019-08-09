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
package zipkin2.server.internal.throttle;

import brave.Tracer;
import brave.Tracing;
import brave.propagation.CurrentTraceContext;
import com.linecorp.armeria.common.brave.RequestContextCurrentTraceContext;
import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.Gradient2Limit;
import com.netflix.concurrency.limits.limiter.AbstractLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import zipkin2.Call;
import zipkin2.Span;
import zipkin2.internal.Nullable;
import zipkin2.server.internal.brave.TracedCall;
import zipkin2.storage.ForwardingStorageComponent;
import zipkin2.storage.SpanConsumer;
import zipkin2.storage.StorageComponent;

import static com.linecorp.armeria.common.util.Exceptions.clearTrace;

/**
 * Delegating implementation that limits requests to the {@link #spanConsumer()} of another {@link
 * StorageComponent}.  The theory here is that this class can be used to:
 * <ul>
 * <li>Prevent spamming the storage engine with excessive, spike requests when they come in; thus
 * preserving it's life.</li>
 * <li>Optionally act as a buffer so that a fixed number requests can be queued for execution when
 * the throttle allows for it.  This optional queue must be bounded in order to avoid running out of
 * memory from infinitely queueing.</li>
 * </ul>
 *
 * @see ThrottledSpanConsumer
 */
public final class ThrottledStorageComponent extends ForwardingStorageComponent {
  /**
   * See {@link ThrottledCall#STORAGE_THROTTLE_MAX_CONCURRENCY} if unfamiliar with clearing trace on
   * exceptions only thrown from one spot.
   */
  static final RejectedExecutionException STORAGE_THROTTLE_MAX_QUEUE_SIZE =
    clearTrace(new RejectedExecutionException("STORAGE_THROTTLE_MAX_QUEUE_SIZE reached"));

  final StorageComponent delegate;
  final @Nullable Tracer tracer;
  final @Nullable CurrentTraceContext currentTraceContext;
  final AbstractLimiter<Void> limiter;
  final ThreadPoolExecutor executor;
  final LimiterMetrics limiterMetrics;

  public ThrottledStorageComponent(StorageComponent delegate, MeterRegistry registry,
    @Nullable Tracing tracing, int minConcurrency, int maxConcurrency, int maxQueueSize) {
    this.delegate = Objects.requireNonNull(delegate);
    this.tracer = tracing != null ? tracing.tracer() : null;
    this.currentTraceContext = tracing != null ? tracing.currentTraceContext() : null;

    Limit limit = Gradient2Limit.newBuilder()
      .minLimit(minConcurrency)
      // Limiter will trend towards min until otherwise necessary so may as well start there
      .initialLimit(minConcurrency)
      .maxConcurrency(maxConcurrency)
      .queueSize(0)
      .build();
    this.limiter = new Builder().limit(limit).build();

    // The size of the thread pool is managed by the limiter, so we initialize it with the lower
    // bound (current limit), and later use change notification to resize it.
    executor = new ThreadPoolExecutor(
      limit.getLimit(),
      limit.getLimit(),
      0,
      TimeUnit.DAYS,
      createQueue(maxQueueSize),
      new NamedThreadFactory("zipkin-throttle-pool") {
        @Override public Thread newThread(Runnable runnable) {
          return super.newThread(new Runnable() {
            @Override public void run() {
              RequestContextCurrentTraceContext.setCurrentThreadNotRequestThread(true);
              runnable.run();
            }

            @Override public String toString() {
              return runnable.toString();
            }
          });
        }
      },
      (r, e) -> {
        throw STORAGE_THROTTLE_MAX_QUEUE_SIZE;
      });
    limit.notifyOnChange(new ThreadPoolExecutorResizer(executor));

    ActuateThrottleMetrics metrics = new ActuateThrottleMetrics(registry);
    metrics.bind(executor);
    metrics.bind(limiter);

    limiterMetrics = new LimiterMetrics(registry);
  }

  @Override protected StorageComponent delegate() {
    return delegate;
  }

  @Override public SpanConsumer spanConsumer() {
    return new ThrottledSpanConsumer(this);
  }

  @Override public void close() throws IOException {
    executor.shutdownNow();
    delegate.close();
  }

  @Override public String toString() {
    return "Throttled{" + delegate.toString() + "}";
  }

  static final class ThrottledSpanConsumer implements SpanConsumer {
    final SpanConsumer delegate;
    final Executor executor;
    final Limiter<Void> limiter;
    final LimiterMetrics limiterMetrics;
    final Predicate<Throwable> isOverCapacity;
    @Nullable final Tracer tracer;

    ThrottledSpanConsumer(ThrottledStorageComponent throttledStorage) {
      this.delegate = throttledStorage.delegate.spanConsumer();
      this.executor = throttledStorage.currentTraceContext != null
        ? throttledStorage.currentTraceContext.executor(throttledStorage.executor)
        : throttledStorage.executor;
      this.limiter = throttledStorage.limiter;
      this.limiterMetrics = throttledStorage.limiterMetrics;
      this.isOverCapacity = throttledStorage::isOverCapacity;
      this.tracer = throttledStorage.tracer;
    }

    @Override public Call<Void> accept(List<Span> spans) {
      Call<Void> result = new ThrottledCall(
        delegate.accept(spans), executor, limiter, limiterMetrics, isOverCapacity);

      return tracer != null ? new TracedCall<>(tracer, result, "throttled-accept-spans") : result;
    }

    @Override public String toString() {
      return "Throttled(" + delegate + ")";
    }
  }

  static BlockingQueue<Runnable> createQueue(int maxSize) {
    if (maxSize < 0) throw new IllegalArgumentException("maxSize < 0");

    if (maxSize == 0) {
      // 0 means we should be bounded but we can't create a queue with that size so use 1 instead.
      maxSize = 1;
    }

    return new LinkedBlockingQueue<>(maxSize);
  }

  static final class ThreadPoolExecutorResizer implements Consumer<Integer> {
    final ThreadPoolExecutor executor;

    ThreadPoolExecutorResizer(ThreadPoolExecutor executor) {
      this.executor = executor;
    }

    /**
     * This is {@code synchronized} to ensure that we don't let the core/max pool sizes get out of
     * sync; even for an instant.  The two need to be tightly coupled together to ensure that when
     * our queue fills up we don't spin up extra Threads beyond our calculated limit.
     *
     * <p>There is also an unfortunate aspect where the {@code max} has to always be greater than
     * {@code core} or an exception will be thrown.  So they have to be adjust appropriately
     * relative to the direction the size is going.
     */
    @Override public synchronized void accept(Integer newValue) {
      int previousValue = executor.getCorePoolSize();

      int newValueInt = newValue;
      if (previousValue < newValueInt) {
        executor.setMaximumPoolSize(newValueInt);
        executor.setCorePoolSize(newValueInt);
      } else if (previousValue > newValueInt) {
        executor.setCorePoolSize(newValueInt);
        executor.setMaximumPoolSize(newValueInt);
      }
      // Note: no case for equals.  Why modify something that doesn't need modified?
    }
  }

  static final class Builder extends AbstractLimiter.Builder<Builder> {
    NonLimitingLimiter build() {
      return new NonLimitingLimiter(this);
    }

    @Override protected Builder self() {
      return this;
    }
  }

  /**
   * Unlike a normal Limiter, this will actually not prevent the creation of a {@link Listener} in
   * {@link #acquire(java.lang.Void)}.  The point of this is to ensure that we can always derive an
   * appropriate {@link Limit#getLimit() Limit} while the {@link #executor} handles actually
   * limiting running requests.
   */
  static final class NonLimitingLimiter extends AbstractLimiter<Void> {
    NonLimitingLimiter(AbstractLimiter.Builder<?> builder) {
      super(builder);
    }

    @Override public Optional<Listener> acquire(Void context) {
      return Optional.of(createListener());
    }
  }
}
