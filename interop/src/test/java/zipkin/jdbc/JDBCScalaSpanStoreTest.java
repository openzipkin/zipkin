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
import zipkin.async.BlockingToAsyncSpanStoreAdapter;
import zipkin.interop.AsyncToScalaSpanStoreAdapter;

import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

public class JDBCScalaSpanStoreTest extends SpanStoreSpec {
  private static JDBCSpanStore spanStore;

  @BeforeClass
  public static void setupDB() throws SQLException {
    spanStore = new JDBCTestGraph().spanStore;
  }

  public SpanStore store() {
    return new AsyncToScalaSpanStoreAdapter(new BlockingToAsyncSpanStoreAdapter(spanStore, directExecutor()));
  }

  public void clear() {
    try {
      spanStore.clear();
    } catch (SQLException e) {
      throw new AssertionError(e);
    }
  }
}
