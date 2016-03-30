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
package zipkin.jdbc;

import com.twitter.zipkin.common.Span;
import com.twitter.zipkin.storage.DependencyStore;
import com.twitter.zipkin.storage.DependencyStoreSpec;
import java.sql.SQLException;
import org.junit.BeforeClass;
import scala.collection.immutable.List;
import zipkin.async.AsyncSpanConsumer;
import zipkin.async.AsyncSpanStore;
import zipkin.async.BlockingToAsyncSpanConsumerAdapter;
import zipkin.async.BlockingToAsyncSpanStoreAdapter;
import zipkin.interop.AsyncToScalaSpanStoreAdapter;
import zipkin.interop.ScalaDependencyStoreAdapter;

public class JDBCScalaDependencyStoreTest extends DependencyStoreSpec {
  private static JDBCSpanStore spanStore;
  private static AsyncSpanStore asyncStore;
  private static AsyncSpanConsumer asyncConsumer;

  @BeforeClass
  public static void setupDB() throws SQLException {
    spanStore = new JDBCTestGraph().spanStore;
    asyncStore = new BlockingToAsyncSpanStoreAdapter(spanStore, Runnable::run);
    asyncConsumer = new BlockingToAsyncSpanConsumerAdapter(spanStore::accept, Runnable::run);
  }

  public DependencyStore store() {
    return new ScalaDependencyStoreAdapter(asyncStore);
  }

  @Override
  public void processDependencies(List<Span> spans) {
    new AsyncToScalaSpanStoreAdapter(asyncStore, asyncConsumer).apply(spans);
  }

  public void clear() {
    try {
      spanStore.clear();
    } catch (SQLException e) {
      throw new AssertionError(e);
    }
  }
}
