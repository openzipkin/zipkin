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
package zipkin.elasticsearch;

import com.twitter.zipkin.storage.SpanStore;
import com.twitter.zipkin.storage.SpanStoreSpec;
import org.junit.BeforeClass;
import zipkin.AsyncSpanConsumer;
import zipkin.AsyncSpanStore;
import zipkin.interop.AsyncToScalaSpanStoreAdapter;

import static zipkin.spanstore.guava.GuavaStorageAdapters.guavaToAsync;

public class ElasticsearchScalaSpanStoreTest extends SpanStoreSpec {
  private static AsyncSpanStore spanStore;
  private static AsyncSpanConsumer spanConsumer;

  @BeforeClass
  public static void setupDB() {
    spanStore = guavaToAsync(ElasticsearchTestGraph.INSTANCE.spanStore());
    spanConsumer = guavaToAsync(ElasticsearchTestGraph.INSTANCE.spanConsumer());
  }

  public SpanStore store() {
    return new AsyncToScalaSpanStoreAdapter(spanStore, spanConsumer);
  }

  public void clear() {
    ElasticsearchTestGraph.INSTANCE.spanStore().clear();
  }
}
