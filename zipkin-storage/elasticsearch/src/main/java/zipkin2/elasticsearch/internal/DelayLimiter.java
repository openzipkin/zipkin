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
package zipkin2.elasticsearch.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/** Limits invocations of a given context to at most once per period */
class DelayLimiter<C> {

  final Map<C, IgnoredContext<C>> cache = new ConcurrentHashMap<>();
  final DelayQueue<IgnoredContext<C>> ignoredContexts = new DelayQueue<>();
  final long periodNanos;
  final long maximumSize = 1000L;

  DelayLimiter(long periodMillis) {
    if (periodMillis < 0) throw new IllegalArgumentException("periodMillis < 0");
    periodNanos = periodMillis * 1000000L;
  }

  /** returns true if the context with the given context should be invoked. */
  boolean shouldInvoke(C context) {
    cleanupExpired();

    if (cache.containsKey(context)) return false;

    IgnoredContext<C> ignoredContext =
      new IgnoredContext<>(context, System.nanoTime() + periodNanos);

    if (cache.putIfAbsent(context, ignoredContext) != null) {
      return false; // lost race
    }

    ignoredContexts.offer(ignoredContext);

    // If we added an entry and it made us go over the intended size
    if (cache.size() > maximumSize) {
      // possible race removing the eldest;
      IgnoredContext<C> eldest;
      while ((eldest = ignoredContexts.peek()) == null || !ignoredContexts.remove(eldest)) {
        if (ignoredContexts.isEmpty()) break;
      }
      if (eldest != null) cache.remove(eldest.context, eldest);
    }

    return true;
  }

  void cleanupExpired() {
    IgnoredContext<C> expiredIgnoredContext;
    while ((expiredIgnoredContext = ignoredContexts.poll()) != null) {
      cache.remove(expiredIgnoredContext.context, expiredIgnoredContext);
    }
  }

  static final class IgnoredContext<C> implements Delayed {
    final C context;
    volatile long expiration;

    IgnoredContext(C context, long expiration) {
      this.context = context;
      this.expiration = expiration;
    }

    @Override public long getDelay(TimeUnit unit) {
      return unit.convert(expiration - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override public int compareTo(Delayed o) {
      return Long.signum(expiration - ((IgnoredContext) o).expiration);
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof DelayLimiter.IgnoredContext)) return false;
      IgnoredContext that = (IgnoredContext) o;
      return context.equals(that.context) && expiration == that.expiration;
    }

    @Override public int hashCode() {
      int h = 1000003;
      h ^= context.hashCode();
      h *= 1000003;
      h ^= (int) ((expiration >>> 32) ^ expiration);
      return h;
    }
  }
}
