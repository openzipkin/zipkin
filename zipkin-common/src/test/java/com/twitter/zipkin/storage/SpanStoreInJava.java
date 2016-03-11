package com.twitter.zipkin.storage;

import scala.collection.Seq;
import scala.collection.immutable.List;
import scala.runtime.BoxedUnit;

import com.twitter.util.Future;
import com.twitter.zipkin.common.Span;

/**
 * Shows that {@link SpanStore} is implementable in Java 7+.
 */
public class SpanStoreInJava extends SpanStore {

    @Override
    public Future<Seq<List<Span>>> getTraces(QueryRequest qr) {
        return null;
    }

    @Override
    public Future<Seq<List<Span>>> getTracesByIds(Seq<Object> traceIds) {
        return null;
    }

    @Override
    public Future<Seq<Seq<Span>>> getSpansByTraceIds(Seq<Object> traceIds) {
        return null;
    }

    @Override
    public Future<Seq<String>> getAllServiceNames() {
        return null;
    }

    @Override
    public Future<Seq<String>> getSpanNames(String service) {
        return null;
    }

    @Override
    public Future<BoxedUnit> apply(Seq<Span> spans) {
        return null;
    }

    @Override
    public void close() {
    }
}
