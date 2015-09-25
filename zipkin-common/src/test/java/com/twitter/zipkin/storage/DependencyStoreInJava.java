package com.twitter.zipkin.storage;

import scala.Option;
import scala.runtime.BoxedUnit;

import com.twitter.util.Future;
import com.twitter.zipkin.common.Dependencies;

/**
 * Shows that {@link DependencyStore} is implementable in Java 7+.
 */
public class DependencyStoreInJava extends DependencyStore {

    @Override
    public void close() {
    }

    @Override
    public Future<Dependencies> getDependencies(Option<Object> startTime, Option<Object> endTime) {
        return null;
    }

    @Override
    public Option<Object> getDependencies$default$2() {
        return null;
    }

    @Override
    public Future<BoxedUnit> storeDependencies(Dependencies dependencies) {
        return null;
    }
}
