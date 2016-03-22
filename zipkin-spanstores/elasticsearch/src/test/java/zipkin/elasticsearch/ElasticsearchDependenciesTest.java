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

import java.util.List;
import java.util.concurrent.TimeUnit;
import zipkin.DependenciesTest;
import zipkin.DependencyLink;
import zipkin.InMemorySpanStore;
import zipkin.Span;
import zipkin.SpanStore;
import zipkin.spanstore.guava.BlockingGuavaSpanStore;

import static zipkin.internal.Util.midnightUTC;

public class ElasticsearchDependenciesTest extends DependenciesTest<SpanStore> {

  public ElasticsearchDependenciesTest() {
    this.store = new BlockingGuavaSpanStore(ElasticsearchTestGraph.INSTANCE.spanStore());
  }

  @Override
  public void clear() {
    ElasticsearchTestGraph.INSTANCE.spanStore().clear();
  }

  /**
   * The current implementation does not include dependency aggregation. It includes retrieval of
   * pre-aggregated links.
   *
   * <p>This uses {@link InMemorySpanStore} to prepare links and {@link
   * ElasticsearchSpanStore#writeDependencyLinks(List, long)}} to store them.
   *
   * <p>Note: The zipkin-dependencies-spark doesn't yet support writing dependency links to
   * elasticsearch, until it does this span store cannot be used for dependency links.
   */
  @Override
  public void processDependencies(List<Span> spans) {
    SpanStore mem = new InMemorySpanStore();
    mem.accept(spans);
    List<DependencyLink> links = mem.getDependencies(today + TimeUnit.DAYS.toMillis(1), null);

    long midnight = midnightUTC(spans.get(0).timestamp / 1000);
    ElasticsearchTestGraph.INSTANCE.spanStore().writeDependencyLinks(
        links, midnight);
  }
}
