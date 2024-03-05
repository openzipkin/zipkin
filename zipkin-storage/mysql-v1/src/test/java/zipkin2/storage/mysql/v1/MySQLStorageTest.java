/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import zipkin2.CheckResult;
import zipkin2.Component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MySQLStorageTest {

  @Test void check_failsInsteadOfThrowing() throws SQLException {
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenThrow(new SQLException("foo"));

    CheckResult result = storage(dataSource).check();

    assertThat(result.ok()).isFalse();
    assertThat(result.error())
      .isInstanceOf(SQLException.class);
  }

  @Test void returns_whitelisted_autocompletekey() throws Exception {
    DataSource dataSource = mock(DataSource.class);
    assertThat(storage(dataSource).autocompleteTags().getKeys().execute())
      .containsOnlyOnce("http.method");
  }

  static MySQLStorage storage(DataSource dataSource) {
    return MySQLStorage.newBuilder()
      .strictTraceId(false)
      .executor(Runnable::run)
      .datasource(dataSource)
      .autocompleteKeys(List.of("http.method"))
      .build();
  }

  /**
   * The {@code toString()} of {@link Component} implementations appear in health check endpoints.
   * Since these are likely to be exposed in logs and other monitoring tools, care should be taken
   * to ensure {@code toString()} output is a reasonable length and does not contain sensitive
   * information.
   */
  @Test void toStringContainsOnlySummaryInformation() {
    DataSource datasource = mock(DataSource.class);
    when(datasource.toString()).thenReturn("Blamo");

    assertThat(storage(datasource)).hasToString("MySQLStorage{datasource=Blamo}");
  }
}
