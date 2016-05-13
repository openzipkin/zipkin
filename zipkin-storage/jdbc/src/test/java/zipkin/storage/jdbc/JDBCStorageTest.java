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
package zipkin.storage.jdbc;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.jooq.exception.DataAccessException;
import org.junit.Test;
import zipkin.Component.CheckResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JDBCStorageTest {

  @Test
  public void check_failsInsteadOfThrowing() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("foo"));

    CheckResult result = JDBCStorage.builder()
        .executor(Runnable::run)
        .datasource(dataSource)
        .build().check();

    assertThat(result.ok).isFalse();
    assertThat(result.exception)
        .isInstanceOf(SQLException.class);
  }
}
