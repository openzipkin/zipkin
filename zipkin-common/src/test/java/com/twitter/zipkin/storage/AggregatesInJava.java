package com.twitter.zipkin.storage;

import scala.Option;
import scala.collection.Seq;
import scala.runtime.BoxedUnit;

import com.twitter.util.Future;
import com.twitter.util.Time;
import com.twitter.zipkin.common.Dependencies;

/**
 * Shows that {@link Aggregates} is implementable in Java 7+.
 */
public class AggregatesInJava extends Aggregates {

    @Override
    public void close() {
    }

    @Override
    public Future<Dependencies> getDependencies(Option<Time> startDate, Option<Time> endDate) {
        return null;
    }

    @Override
    public Option<Time> getDependencies$default$2() {
        return null;
    }

    @Override
    public Future<BoxedUnit> storeDependencies(Dependencies dependencies) {
        return null;
    }

    @Override
    public Future<Seq<String>> getTopAnnotations(String serviceName) {
        return null;
    }

    @Override
    public Future<Seq<String>> getTopKeyValueAnnotations(String serviceName) {
        return null;
    }

    @Override
    public Future<BoxedUnit> storeTopAnnotations(String serviceName, Seq<String> a) {
        return null;
    }

    @Override
    public Future<BoxedUnit> storeTopKeyValueAnnotations(String serviceName, Seq<String> a) {
        return null;
    }
}
