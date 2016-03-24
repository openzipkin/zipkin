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
package zipkin.cassandra;

import com.google.common.util.concurrent.Futures;
import com.twitter.zipkin.common.Span;
import com.twitter.zipkin.storage.DependencyStore;
import com.twitter.zipkin.storage.DependencyStoreSpec;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import org.junit.BeforeClass;
import scala.collection.immutable.List;
import zipkin.DependencyLink;
import zipkin.InMemorySpanStore;
import zipkin.internal.Dependencies;
import zipkin.interop.AsyncToScalaSpanStoreAdapter;
import zipkin.interop.ScalaDependencyStoreAdapter;

import static zipkin.internal.Util.midnightUTC;

public class CassandraScalaDependencyStoreTest extends DependencyStoreSpec {
  private static CassandraSpanStore spanStore;

  @BeforeClass
  public static void setupDB() {
    spanStore = CassandraTestGraph.INSTANCE.spanStore();
  }

  public DependencyStore store() {
    return new ScalaDependencyStoreAdapter(spanStore);
  }

  @Override
  public void processDependencies(List<Span> input) {
    InMemorySpanStore mem = new InMemorySpanStore();
    new AsyncToScalaSpanStoreAdapter(mem).apply(input);
    java.util.List<DependencyLink>
        links = mem.getDependencies(today() + TimeUnit.DAYS.toMillis(1), null);

    long midnight = midnightUTC(((long) input.apply(0).timestamp().get()) / 1000);
    Dependencies deps = Dependencies.create(midnight, midnight /* ignored */, links);
    ByteBuffer thrift = deps.toThrift();
    // Block on the future to get read-your-writes consistency during tests
    Futures.getUnchecked(CassandraTestGraph.INSTANCE.spanStore().repository.storeDependencies(midnight, thrift));
  }

  public void clear() {
    CassandraTestGraph.INSTANCE.spanStore().clear();
  }
}
