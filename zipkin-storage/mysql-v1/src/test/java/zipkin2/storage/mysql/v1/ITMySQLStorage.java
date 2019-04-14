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
package zipkin2.storage.mysql.v1;

import java.sql.Connection;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import zipkin2.DependencyLink;
import zipkin2.storage.StorageComponent;

import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinDependencies.ZIPKIN_DEPENDENCIES;

@RunWith(Enclosed.class)
public class ITMySQLStorage {

  static LazyMySQLStorage classRule() {
    return new LazyMySQLStorage("2.12.7");
  }

  public static class ITSpanStore extends zipkin2.storage.ITSpanStore {
    @ClassRule public static LazyMySQLStorage storage = classRule();

    @Override protected StorageComponent storage() {
      return storage.get();
    }

    @Override @Test @Ignore("No consumer-side span deduplication") public void deduplicates() {
    }

    @Override public void clear() {
      storage.get().clear();
    }
  }

  public static class ITStrictTraceIdFalse extends zipkin2.storage.ITStrictTraceIdFalse {
    @ClassRule public static LazyMySQLStorage storageRule = classRule();

    MySQLStorage storage;

    @Override protected StorageComponent storage() {
      return storage;
    }

    @Override public void clear() {
      storage = storageRule.computeStorageBuilder().strictTraceId(false).build();
      storage.clear();
    }
  }

  public static class ITSearchEnabledFalse extends zipkin2.storage.ITSearchEnabledFalse {
    @ClassRule public static LazyMySQLStorage storageRule = classRule();

    MySQLStorage storage;

    @Override protected StorageComponent storage() {
      return storage;
    }

    @Override public void clear() {
      storage = storageRule.computeStorageBuilder().searchEnabled(false).build();
      storage.clear();
    }
  }

  public static class ITDependenciesPreAggregated extends zipkin2.storage.ITDependencies {
    @ClassRule public static LazyMySQLStorage storage = classRule();

    @Override protected StorageComponent storage() {
      return storage.get();
    }

    /**
     * The current implementation does not include dependency aggregation. It includes retrieval of
     * pre-aggregated links, usually made via zipkin-dependencies
     */
    @Override protected void processDependencies(List<zipkin2.Span> spans) throws Exception {
      try (Connection conn = storage.get().datasource.getConnection()) {
        DSLContext context = storage.get().context.get(conn);

        // batch insert the rows at timestamp midnight
        List<Query> inserts = new ArrayList<>();
        aggregateLinks(spans).forEach((midnight, links) -> {
          Date day = new Date(midnight);
          for (DependencyLink link : links) {
            inserts.add(context.insertInto(ZIPKIN_DEPENDENCIES)
              .set(ZIPKIN_DEPENDENCIES.DAY, day)
              .set(ZIPKIN_DEPENDENCIES.PARENT, link.parent())
              .set(ZIPKIN_DEPENDENCIES.CHILD, link.child())
              .set(ZIPKIN_DEPENDENCIES.CALL_COUNT, link.callCount())
              .set(ZIPKIN_DEPENDENCIES.ERROR_COUNT, link.errorCount())
              .onDuplicateKeyIgnore());
          }
        });
        context.batch(inserts).execute();
      }
    }

    @Override public void clear() {
      storage.get().clear();
    }
  }

  public static class ITServiceAndSpanNames extends zipkin2.storage.ITServiceAndSpanNames {
    @ClassRule public static LazyMySQLStorage storage = classRule();

    @Override protected StorageComponent storage() {
      return storage.get();
    }

    @Override public void clear() {
      storage.get().clear();
    }
  }

  public static class ITAutocompleteTags extends zipkin2.storage.ITAutocompleteTags {
    @ClassRule public static LazyMySQLStorage storage = classRule();

    @Override protected StorageComponent.Builder storageBuilder() {
      return storage.computeStorageBuilder();
    }

    @Before @Override public void clear() {
      storage.get().clear();
    }
  }

  public static class ITDependenciesOnDemand extends zipkin2.storage.ITDependencies {
    @ClassRule public static LazyMySQLStorage storage = classRule();

    @Override protected StorageComponent storage() {
      return storage.get();
    }

    @Override public void clear() {
      storage.get().clear();
    }
  }
}
