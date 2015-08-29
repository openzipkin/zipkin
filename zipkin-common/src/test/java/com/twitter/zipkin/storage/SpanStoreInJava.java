package com.twitter.zipkin.storage;

import java.nio.ByteBuffer;

import scala.Option;
import scala.collection.Seq;
import scala.collection.immutable.Set;
import scala.runtime.BoxedUnit;

import com.twitter.util.Duration;
import com.twitter.util.Future;
import com.twitter.zipkin.common.Span;

/**
 * Shows that {@link SpanStore} is implementable in Java 7+.
 */
public class SpanStoreInJava extends SpanStore {

    @Override
    public Future<Duration> getTimeToLive(long traceId) {
        return null;
    }

    @Override
    public Future<Set<Object>> tracesExist(Seq<Object> traceIds) {
        return null;
    }

    @Override
    public Future<Seq<Seq<Span>>> getSpansByTraceIds(Seq<Object> traceIds) {
        return null;
    }

    @Override
    public Future<Seq<Span>> getSpansByTraceId(long traceId) {
        return null;
    }

    @Override
    public Future<Seq<IndexedTraceId>> getTraceIdsByName(String serviceName, Option<String> spanName, long endTs, int limit) {
        return null;
    }

    @Override
    public Future<Seq<IndexedTraceId>> getTraceIdsByAnnotation(String serviceName, String annotation, Option<ByteBuffer> value, long endTs, int limit) {
        return null;
    }

    @Override
    public Future<Set<String>> getAllServiceNames() {
        return null;
    }

    @Override
    public Future<Set<String>> getSpanNames(String service) {
        return null;
    }

    @Override
    public Future<BoxedUnit> apply(Seq<Span> spans) {
        return null;
    }

    @Override
    public Future<BoxedUnit> setTimeToLive(long traceId, Duration ttl) {
        return null;
    }

    @Override
    public void close() {
    }
}
