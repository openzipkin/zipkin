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

import java.sql.SQLException;
import java.util.List;
import zipkin.DependenciesTest;
import zipkin.Span;
import zipkin.SpanStoreTest;

public class JDBCDependenciesTest extends DependenciesTest<JDBCSpanStore> {

  public JDBCDependenciesTest() throws SQLException {
    super(new JDBCTestGraph().spanStore);
  }

  @Override
  public void clear() {
    try {
      store.clear();
    } catch (SQLException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  protected void processDependencies(List<Span> spans) {
    store.accept(spans.iterator());
  }
}
