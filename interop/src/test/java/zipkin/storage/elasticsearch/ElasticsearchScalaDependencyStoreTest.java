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

import com.twitter.zipkin.common.Span;
import com.twitter.zipkin.storage.DependencyStore;
import com.twitter.zipkin.storage.DependencyStoreSpec;
import java.util.concurrent.TimeUnit;
import scala.collection.immutable.List;
import zipkin.DependencyLink;
import zipkin.storage.interop.ScalaDependencyStoreAdapter;
import zipkin.storage.interop.ScalaSpanStoreAdapter;
import zipkin.storage.InMemoryStorage;

import static zipkin.internal.Util.midnightUTC;
import static zipkin.storage.guava.GuavaStorageAdapters.guavaToAsync;

public class ElasticsearchScalaDependencyStoreTest extends DependencyStoreSpec {
  private final ElasticsearchStorage storage;

  public ElasticsearchScalaDependencyStoreTest() {
    this.storage = ElasticsearchTestGraph.INSTANCE.storage.get();
  }

  public DependencyStore store() {
    return new ScalaDependencyStoreAdapter(guavaToAsync(storage.guavaSpanStore()));
  }

  @Override
  public void processDependencies(List<Span> input) {
    InMemoryStorage mem = new InMemoryStorage();
    new ScalaSpanStoreAdapter(mem).apply(input);
    java.util.List<DependencyLink>
        links = mem.spanStore().getDependencies(today() + TimeUnit.DAYS.toMillis(1), null);

    long midnight = midnightUTC(((long) input.apply(0).timestamp().get()) / 1000);
    storage.writeDependencyLinks(links, midnight);
  }

  public void clear() {
    storage.clear();
  }
}
