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

import org.elasticsearch.action.admin.indices.flush.FlushRequest;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.transport.TransportClient;
import org.junit.AfterClass;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import zipkin.DependencyLink;
import zipkin.internal.Util;
import zipkin.storage.StorageComponent;

import java.io.IOException;
import java.util.List;

@RunWith(Enclosed.class)
public class ElasticsearchV5TransportTest {

  private static final LazyElasticsearchTransportStorage storage =
      new LazyElasticsearchTransportStorage("elasticsearch:5.0.1");

  @AfterClass
  public static void destroy() throws Exception {
    storage.close();
  }

  public static class DependenciesTest extends ElasticsearchDependenciesTest {

    @Override protected ElasticsearchStorage storage() {
      return storage.get();
    }

    protected void writeDependencyLinks(List<DependencyLink> links, long timestampMillis) {
      long midnight = Util.midnightUTC(timestampMillis);
      TransportClient client = ((NativeClient) storage().client()).client;
      BulkRequestBuilder request = client.prepareBulk();
      for (DependencyLink link : links) {
        request.add(client.prepareIndex(
            storage().indexNameFormatter.indexNameForTimestamp(midnight),
            ElasticsearchConstants.DEPENDENCY_LINK)
            .setId(link.parent + "|" + link.child) // Unique constraint
            .setSource(
                "parent", link.parent,
                "child", link.child,
                "callCount", link.callCount));
      }
      request.execute().actionGet();
      client.admin().indices().flush(new FlushRequest()).actionGet();
    }
  }

  public static class ElasticsearchSpanConsumerTest extends zipkin.storage.elasticsearch.ElasticsearchSpanConsumerTest {

    @Override protected ElasticsearchStorage storage() {
      return storage.get();
    }
  }

  public static class SpanStoreTest extends zipkin.storage.SpanStoreTest {

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
      ElasticsearchV5TransportTest.storage.get();
      storage = ElasticsearchV5TransportTest.storage.computeStorageBuilder()
          .strictTraceId(false)
          .index("test_zipkin_transport_mixed").build();
    }

    @Override protected StorageComponent storage() {
      return storage;
    }

    @Override public void clear() throws IOException {
      storage.clear();
    }
  }
}
