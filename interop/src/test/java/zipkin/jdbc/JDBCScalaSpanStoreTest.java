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

import com.twitter.zipkin.storage.SpanStore;
import com.twitter.zipkin.storage.SpanStoreSpec;
import java.sql.SQLException;
import org.junit.BeforeClass;
import zipkin.AsyncSpanConsumer;
import zipkin.AsyncSpanStore;
import zipkin.interop.AsyncToScalaSpanStoreAdapter;

import static zipkin.StorageAdapters.blockingToAsync;

public class JDBCScalaSpanStoreTest extends SpanStoreSpec {
  private static JDBCSpanStore store;
  private static AsyncSpanStore asyncStore;
  private static AsyncSpanConsumer asyncConsumer;

  @BeforeClass
  public static void setupDB() throws SQLException {
    store = new JDBCTestGraph().spanStore;
    asyncStore = blockingToAsync(store, Runnable::run);
    asyncConsumer = blockingToAsync(store::accept, Runnable::run);
  }

  public SpanStore store() {
    return new AsyncToScalaSpanStoreAdapter(asyncStore, asyncConsumer);
  }

  public void clear() {
    try {
      store.clear();
    } catch (SQLException e) {
      throw new AssertionError(e);
    }
  }
}
