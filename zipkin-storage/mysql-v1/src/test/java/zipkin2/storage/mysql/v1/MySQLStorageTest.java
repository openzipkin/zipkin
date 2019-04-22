/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.storage.mysql.v1;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.Test;
import zipkin2.CheckResult;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MySQLStorageTest {

  @Test public void check_failsInsteadOfThrowing() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("foo"));

    CheckResult result = storage(dataSource).check();

    assertThat(result.ok()).isFalse();
    assertThat(result.error())
      .isInstanceOf(SQLException.class);
  }

  @Test public void returns_whitelisted_autocompletekey() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    assertThat(storage(dataSource).autocompleteTags().getKeys().execute())
      .containsOnlyOnce("http.method");
  }

  static MySQLStorage storage(DataSource dataSource) {
    return MySQLStorage.newBuilder()
      .strictTraceId(false)
      .executor(Runnable::run)
      .datasource(dataSource)
      .autocompleteKeys(asList("http.method"))
      .build();
  }
}
