/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.storage.mysql;

import java.util.List;
import org.junit.ClassRule;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import zipkin.Span;
import zipkin.internal.MergeById;
import zipkin.storage.StorageComponent;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Enclosed.class)
public class ITMySQLStorage {

  static LazyMySQLStorage classRule() {
    return new LazyMySQLStorage("2.4.1");
  }

  public static class DependenciesTest extends zipkin.storage.DependenciesTest {
    @ClassRule public static LazyMySQLStorage storage = classRule();

    @Override protected StorageComponent storage() {
      return storage.get();
    }

    @Override public void clear() {
      storage.get().clear();
    }
  }

  public static class SpanStoreTest extends zipkin.storage.SpanStoreTest {
    @ClassRule public static LazyMySQLStorage storage = classRule();

    @Override protected StorageComponent storage() {
      return storage.get();
    }

    @Override
    public void clear() {
      storage.get().clear();
    }
  }

  public static class StrictTraceIdFalseTest extends zipkin.storage.StrictTraceIdFalseTest {
    @ClassRule public static LazyMySQLStorage storageRule = classRule();

    private MySQLStorage storage;

    @Override protected StorageComponent storage() {
      return storage;
    }

    /** current implementation cannot return exact form reported */
    @Override public void getTrace_retrievesBy64Or128BitTraceId() {
      List<Span> trace = MergeById.apply(accept128BitTrace(storage()));
      assertThat(store().getTrace(0L, trace.get(0).traceId))
        .containsOnlyElementsOf(trace);
      assertThat(store().getTrace(trace.get(0).traceIdHigh, trace.get(0).traceId))
        .containsOnlyElementsOf(trace);
    }

    @Override public void clear() {
      storage = storageRule.computeStorageBuilder()
        .strictTraceId(false)
        .build();
      storage.clear();
    }
  }
}
