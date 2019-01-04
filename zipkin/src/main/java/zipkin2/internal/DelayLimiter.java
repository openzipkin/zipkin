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
package zipkin2.internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/** Limits invocations of a given context to at most once per period. */
// this is a dependency-free variant formerly served by an expiring guava cache
public final class DelayLimiter<C> {

  public static <C> DelayLimiter<C> create() {
    return new Builder().build();
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    long ttlNanos = TimeUnit.HOURS.toNanos(1); // legacy default from cassandra
    int maximumSize = 5 * 1000; // Ex. 5 site tags with cardinality 1000 each

    /**
     * When {@link #shouldInvoke(Object)} returns true, it will return false until this duration
     * expires.
     */
    public Builder ttl(int ttl) {
      if (ttl <= 0) throw new IllegalArgumentException("ttl <= 0");
      this.ttlNanos = TimeUnit.MILLISECONDS.toNanos(ttl);
      return this;
    }

    /**
     * This bounds supressions, useful because contexts can be accidentally unlimited cardinality.
     */
    public Builder maxSize(int maximumSize) {
      if (maximumSize <= 0) throw new IllegalArgumentException("maxSize <= 0");
      this.maximumSize = maximumSize;
      return this;
    }

    public <C> DelayLimiter<C> build() {
      return new DelayLimiter<>(this);
    }

    Builder() {
    }
  }

  final ConcurrentHashMap<C, Suppression<C>> cache = new ConcurrentHashMap<>();
  final DelayQueue<Suppression<C>> suppressions = new DelayQueue<>();
  final long ttlNanos, maximumSize;

  DelayLimiter(Builder builder) {
    ttlNanos = builder.ttlNanos;
    maximumSize = builder.maximumSize;
  }

  /** Returns true if a given context should be invoked. */
  public boolean shouldInvoke(C context) {
    cleanupExpiredSuppressions();

    if (cache.containsKey(context)) return false;

    Suppression<C> suppression = new Suppression<>(context, System.nanoTime() + ttlNanos);

    if (cache.putIfAbsent(context, suppression) != null) return false; // lost race

    suppressions.offer(suppression);

    // If we added an entry, it could make us go over the max size. This loops until we are no more
    // than max size.
    while (suppressions.size() > maximumSize) {
      tryClearOneSuppression();
    }

    return true;
  }

  public void invalidate(C context) {
    Suppression<C> suppression = cache.remove(context);
    if (suppression != null) suppressions.remove(suppression);
  }

  public void clear() {
    cache.clear();
    suppressions.clear();
  }

  /** This attempts to remove the oldest entry to free up one slot */
  void tryClearOneSuppression() {
    // This double-checks that another thread didn't remove the same context we peeked
    Suppression<C> eldest;
    if ((eldest = suppressions.peek()) != null && suppressions.remove(eldest)) {
      // This double-checks that another thread didn't resolve the space problem
      if (cache.size() > maximumSize) {
        cache.remove(eldest.context, eldest);
      } else { // We lost a race so should put the eldest back
        suppressions.offer(eldest);
      }
    }
  }

  void cleanupExpiredSuppressions() {
    Suppression<C> expiredSuppression;
    while ((expiredSuppression = suppressions.poll()) != null) {
      cache.remove(expiredSuppression.context, expiredSuppression);
    }
  }

  static final class Suppression<C> implements Delayed {
    final C context;
    final long expiration;

    Suppression(C context, long expiration) {
      this.context = context;
      this.expiration = expiration;
    }

    @Override public long getDelay(TimeUnit unit) {
      return unit.convert(expiration - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override public int compareTo(Delayed o) {
      return Long.signum(expiration - ((Suppression) o).expiration);
    }
  }
}
