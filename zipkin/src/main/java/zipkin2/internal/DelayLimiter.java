/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
  public static Builder newBuilder() {
    return new Builder();
  }

  public static final class Builder {
    long ttl = 0L;
    TimeUnit ttlUnit = TimeUnit.MILLISECONDS;
    int cardinality = 0;

    /**
     * When {@link #shouldInvoke(Object)} returns true, it will return false until this duration
     * expires.
     */
    public Builder ttl(long ttl, TimeUnit ttlUnit) {
      if (ttlUnit == null) throw new NullPointerException("ttlUnit == null");
      this.ttl = ttl;
      this.ttlUnit = ttlUnit;
      return this;
    }

    /**
     * This bounds suppressions, useful because contexts can be accidentally unlimited cardinality.
     */
    public Builder cardinality(int cardinality) {
      this.cardinality = cardinality;
      return this;
    }

    public <C> DelayLimiter<C> build() {
      if (ttl <= 0L) throw new IllegalArgumentException("ttl <= 0");
      if (cardinality <= 0) throw new IllegalArgumentException("cardinality <= 0");
      return new DelayLimiter<C>(new SuppressionFactory(ttlUnit.toNanos(ttl)), cardinality);
    }

    Builder() {
    }
  }

  final SuppressionFactory suppressionFactory;
  final ConcurrentHashMap<C, Suppression<C>> cache = new ConcurrentHashMap<C, Suppression<C>>();
  final DelayQueue<Suppression<C>> suppressions = new DelayQueue<Suppression<C>>();
  final int cardinality;

  DelayLimiter(SuppressionFactory suppressionFactory, int cardinality) {
    this.suppressionFactory = suppressionFactory;
    this.cardinality = cardinality;
  }

  /** Returns true if a given context should be invoked. */
  public boolean shouldInvoke(C context) {
    cleanupExpiredSuppressions();

    if (cache.containsKey(context)) return false;

    Suppression<C> suppression = suppressionFactory.create(context);

    if (cache.putIfAbsent(context, suppression) != null) return false; // lost race

    suppressions.offer(suppression);

    // If we added an entry, it could make us go over the max size.
    if (suppressions.size() > cardinality) removeOneSuppression();

    return true;
  }

  void removeOneSuppression() {
    Suppression<C> eldest;
    while ((eldest = suppressions.peek()) != null) { // loop unless empty
      if (suppressions.remove(eldest)) { // check for lost race
        cache.remove(eldest.context, eldest);
        break; // to ensure we don't remove two!
      }
    }
  }

  public void invalidate(C context) {
    Suppression<C> suppression = cache.remove(context);
    if (suppression != null) suppressions.remove(suppression);
  }

  public void clear() {
    cache.clear();
    suppressions.clear();
  }

  void cleanupExpiredSuppressions() {
    Suppression<C> expiredSuppression;
    while ((expiredSuppression = suppressions.poll()) != null) {
      cache.remove(expiredSuppression.context, expiredSuppression);
    }
  }

  static class SuppressionFactory { // not final for tests
    final long ttlNanos;

    SuppressionFactory(long ttlNanos) {
      this.ttlNanos = ttlNanos;
    }

    long nanoTime() {
      return System.nanoTime();
    }

    <C> Suppression<C> create(C context) {
      return new Suppression<C>(this, context, nanoTime() + ttlNanos);
    }
  }

  static final class Suppression<C> implements Delayed {
    final SuppressionFactory factory;
    final C context;
    final long expiration;

    Suppression(SuppressionFactory factory, C context, long expiration) {
      this.factory = factory;
      this.context = context;
      this.expiration = expiration;
    }

    @Override public long getDelay(TimeUnit unit) {
      return unit.convert(expiration - factory.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override public int compareTo(Delayed o) {
      return Long.signum(expiration - ((Suppression) o).expiration);
    }
  }
}
