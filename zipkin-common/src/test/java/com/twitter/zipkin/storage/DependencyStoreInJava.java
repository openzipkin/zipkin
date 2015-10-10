package com.twitter.zipkin.storage;

import com.twitter.util.Future;
import com.twitter.zipkin.common.Dependencies;
import com.twitter.zipkin.common.DependencyLink;
import scala.Option;
import scala.collection.Seq;
import scala.runtime.BoxedUnit;

/**
 * Shows that {@link DependencyStore} is implementable in Java 7+.
 */
public class DependencyStoreInJava extends DependencyStore {

    @Override
    public void close() {
    }

    @Override
    public Future<Seq<DependencyLink>> getDependencies(Option<Object> startTs, Option<Object> endTs) {
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
