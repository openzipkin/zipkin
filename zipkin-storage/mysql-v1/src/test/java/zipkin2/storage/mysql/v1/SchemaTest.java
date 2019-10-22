/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.storage.mysql.v1;

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import javax.sql.DataSource;
import org.jooq.conf.Settings;
import org.jooq.exception.DataAccessException;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SchemaTest {
  DataSource dataSource = mock(DataSource.class);
  Schema schema = new Schema(
    dataSource,
    new DSLContexts(new Settings().withRenderSchema(false), null),
    true
  );

  @Test
  public void hasIpv6_falseWhenKnownSQLState() throws SQLException {
    SQLSyntaxErrorException sqlException = new SQLSyntaxErrorException(
        "Unknown column 'zipkin_annotations.endpoint_ipv6' in 'field list'",
        "42S22", 1054);

    // cheats to lower mock count: this exception is really thrown during execution of the query
    when(dataSource.getConnection()).thenThrow(
        new DataAccessException(sqlException.getMessage(), sqlException));

    assertThat(schema.hasIpv6).isFalse();
  }

  /**
   * This returns false instead of failing when the SQLState code doesn't imply the column is
   * missing. This is to prevent zipkin from crashing due to scenarios we haven't thought up, yet.
   * The root error goes into the log in this case.
   */
  @Test
  public void hasIpv6_falseWhenUnknownSQLState() throws SQLException {
    SQLSyntaxErrorException sqlException = new SQLSyntaxErrorException(
        "java.sql.SQLSyntaxErrorException: Table 'zipkin.zipkin_annotations' doesn't exist",
        "42S02", 1146);
    DataSource dataSource = mock(DataSource.class);

    // cheats to lower mock count: this exception is really thrown during execution of the query
    when(dataSource.getConnection()).thenThrow(
        new DataAccessException(sqlException.getMessage(), sqlException));

    assertThat(schema.hasIpv6).isFalse();
  }

  @Test
  public void hasErrorCount_falseWhenKnownSQLState() throws SQLException {
    SQLSyntaxErrorException sqlException = new SQLSyntaxErrorException(
      "Unknown column 'zipkin_dependencies.error_count' in 'field list'",
      "42S22", 1054);

    // cheats to lower mock count: this exception is really thrown during execution of the query
    when(dataSource.getConnection()).thenThrow(
      new DataAccessException(sqlException.getMessage(), sqlException));

    assertThat(schema.hasErrorCount).isFalse();
  }

  /**
   * This returns false instead of failing when the SQLState code doesn't imply the column is
   * missing. This is to prevent zipkin from crashing due to scenarios we haven't thought up, yet.
   * The root error goes into the log in this case.
   */
  @Test
  public void hasErrorCount_falseWhenUnknownSQLState() throws SQLException {
    SQLSyntaxErrorException sqlException = new SQLSyntaxErrorException(
      "java.sql.SQLSyntaxErrorException: Table 'zipkin.zipkin_dependencies' doesn't exist",
      "42S02", 1146);
    DataSource dataSource = mock(DataSource.class);

    // cheats to lower mock count: this exception is really thrown during execution of the query
    when(dataSource.getConnection()).thenThrow(
      new DataAccessException(sqlException.getMessage(), sqlException));

    assertThat(schema.hasErrorCount).isFalse();
  }

  @Test
  public void hasDependencies_missing() throws SQLException {
    SQLSyntaxErrorException sqlException = new SQLSyntaxErrorException(
        "SQL [select count(*) from `zipkin_dependencies`]; Table 'zipkin.zipkin_dependencies' doesn't exist\n"
            + "  Query is : select count(*) from `zipkin_dependencies`",
        "42S02", 1146);
    DataSource dataSource = mock(DataSource.class);

    // cheats to lower mock count: this exception is really thrown during execution of the query
    when(dataSource.getConnection()).thenThrow(
        new DataAccessException(sqlException.getMessage(), sqlException));

    assertThat(schema.hasPreAggregatedDependencies).isFalse();
  }

  @Test
  public void hasRemoteServiceName_falseWhenKnownSQLState() throws SQLException {
    SQLSyntaxErrorException sqlException = new SQLSyntaxErrorException(
      "Unknown column 'zipkin_spans.remote_serviceName' in 'field list'",
      "42S22", 1054);

    // cheats to lower mock count: this exception is really thrown during execution of the query
    when(dataSource.getConnection()).thenThrow(
      new DataAccessException(sqlException.getMessage(), sqlException));

    assertThat(schema.hasRemoteServiceName).isFalse();
  }
}
