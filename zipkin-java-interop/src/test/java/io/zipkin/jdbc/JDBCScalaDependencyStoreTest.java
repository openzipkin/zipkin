/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.jdbc;

import com.twitter.zipkin.common.Span;
import com.twitter.zipkin.storage.DependencyStore;
import com.twitter.zipkin.storage.DependencyStoreSpec;
import io.zipkin.interop.ScalaDependencyStoreAdapter;
import io.zipkin.interop.ScalaSpanStoreAdapter;
import java.sql.SQLException;
import org.junit.BeforeClass;
import scala.collection.immutable.List;

public class JDBCScalaDependencyStoreTest extends DependencyStoreSpec {
  private static JDBCSpanStore spanStore;

  @BeforeClass
  public static void setupDB() {
    spanStore = new JDBCTestGraph().spanStore;
  }

  public DependencyStore store() {
    return new ScalaDependencyStoreAdapter(spanStore);
  }

  @Override
  public void processDependencies(List<Span> spans) {
    new ScalaSpanStoreAdapter(spanStore).apply(spans);
  }

  public void clear() {
    try {
      spanStore.clear();
    } catch (SQLException e) {
      throw new AssertionError(e);
    }
  }
}
