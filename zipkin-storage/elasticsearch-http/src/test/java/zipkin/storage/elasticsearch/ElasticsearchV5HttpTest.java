/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.storage.elasticsearch;

import com.google.common.base.Throwables;
import org.junit.AfterClass;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import zipkin.DependencyLink;
import zipkin.internal.Util;
import zipkin.storage.SpanStoreTest;
import zipkin.storage.elasticsearch.http.HttpElasticsearchDependencyWriter;

import java.io.IOException;
import java.util.List;

@RunWith(Enclosed.class)
public class ElasticsearchV5HttpTest {

  private static final LazyElasticsearchHttpStorage storage =
      new LazyElasticsearchHttpStorage("elasticsearch:5.0.1");

  @AfterClass
  public static void destroy() throws Exception {
    storage.close();
  }

  public static class DependenciesTest extends ElasticsearchDependenciesTest {

    @Override protected ElasticsearchStorage storage() {
      return storage.get();
    }

    @Override protected void writeDependencyLinks(List<DependencyLink> links, long timestampMillis) {
      long midnight = Util.midnightUTC(timestampMillis);
      String index = storage.get().indexNameFormatter.indexNameForTimestamp(midnight);
      try {
        HttpElasticsearchDependencyWriter.writeDependencyLinks(storage.get().client(), links, index,
            ElasticsearchConstants.DEPENDENCY_LINK);
      } catch (Exception ex) {
        throw Throwables.propagate(ex);
      }
    }
  }

  public static class SpanConsumerTest extends ElasticsearchSpanConsumerTest {

    @Override protected ElasticsearchStorage storage() {
      return storage.get();
    }
  }

  public static class ElasticsearchSpanStoreTest extends SpanStoreTest {

    @Override protected ElasticsearchStorage storage() {
      return storage.get();
    }

    @Override public void clear() throws IOException {
      storage().clear();
    }
  }

  public static class StrictTraceIdFalseTest extends zipkin.storage.StrictTraceIdFalseTest {

    private final ElasticsearchStorage storage;

    public StrictTraceIdFalseTest() throws IOException {
      // verify all works ok
      ElasticsearchV5HttpTest.storage.get();
      storage = ElasticsearchV5HttpTest.storage.computeStorageBuilder()
          .strictTraceId(false)
          .index("test_zipkin_http_mixed").build();
    }

    @Override protected ElasticsearchStorage storage() {
      return storage;
    }

    @Override public void clear() throws IOException {
      storage().clear();
    }
  }
}
