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

import com.twitter.zipkin.common.Span;
import com.twitter.zipkin.storage.DependencyStore;
import com.twitter.zipkin.storage.DependencyStoreSpec;
import java.util.concurrent.TimeUnit;
import org.junit.BeforeClass;
import scala.collection.immutable.List;
import zipkin.DependencyLink;
import zipkin.InMemorySpanStore;
import zipkin.SpanStore;
import zipkin.interop.ScalaDependencyStoreAdapter;
import zipkin.interop.ScalaSpanStoreAdapter;
import zipkin.spanstore.guava.BlockingGuavaSpanStore;

import static zipkin.internal.Util.midnightUTC;

public class ElasticsearchScalaDependencyStoreTest extends DependencyStoreSpec {
  private static ElasticsearchSpanStore spanStore;

  @BeforeClass
  public static void setupDB() {
    spanStore = ElasticsearchTestGraph.INSTANCE.spanStore();
  }

  public DependencyStore store() {
    return new ScalaDependencyStoreAdapter(new BlockingGuavaSpanStore(spanStore));
  }

  @Override
  public void processDependencies(List<Span> input) {
    SpanStore mem = new InMemorySpanStore();
    new ScalaSpanStoreAdapter(mem).apply(input);
    java.util.List<DependencyLink>
        links = mem.getDependencies(today() + TimeUnit.DAYS.toMillis(1), null);

    long midnight = midnightUTC(((long) input.apply(0).timestamp().get()) / 1000);
    ElasticsearchTestGraph.INSTANCE.spanStore().writeDependencyLinks(links, midnight);
  }

  public void clear() {
    spanStore.clear();
  }
}
