/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;
import javax.sql.DataSource;
import org.jooq.conf.Settings;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchemaTest {
  DataSource dataSource = mock(DataSource.class);
  Schema schema = new Schema(
    dataSource,
    new DSLContexts(new Settings().withRenderSchema(false), null),
    true
  );

  @Test void hasIpv6_falseWhenKnownSQLState() throws SQLException {
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
  @Test void hasIpv6_falseWhenUnknownSQLState() throws SQLException {
    SQLSyntaxErrorException sqlException = new SQLSyntaxErrorException(
        "java.sql.SQLSyntaxErrorException: Table 'zipkin.zipkin_annotations' doesn't exist",
        "42S02", 1146);
    DataSource dataSource = mock(DataSource.class);

    // cheats to lower mock count: this exception is really thrown during execution of the query
    when(dataSource.getConnection()).thenThrow(
        new DataAccessException(sqlException.getMessage(), sqlException));

    assertThat(schema.hasIpv6).isFalse();
  }

  @Test void hasErrorCount_falseWhenKnownSQLState() throws SQLException {
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
  @Test void hasErrorCount_falseWhenUnknownSQLState() throws SQLException {
    SQLSyntaxErrorException sqlException = new SQLSyntaxErrorException(
      "java.sql.SQLSyntaxErrorException: Table 'zipkin.zipkin_dependencies' doesn't exist",
      "42S02", 1146);
    DataSource dataSource = mock(DataSource.class);

    // cheats to lower mock count: this exception is really thrown during execution of the query
    when(dataSource.getConnection()).thenThrow(
      new DataAccessException(sqlException.getMessage(), sqlException));

    assertThat(schema.hasErrorCount).isFalse();
  }

  @Test void hasDependencies_missing() throws SQLException {
    SQLSyntaxErrorException sqlException = new SQLSyntaxErrorException(
        """
        SQL [select count(*) from `zipkin_dependencies`]; Table 'zipkin.zipkin_dependencies' doesn't exist
          Query is : select count(*) from `zipkin_dependencies`\
        """,
        "42S02", 1146);
    DataSource dataSource = mock(DataSource.class);

    // cheats to lower mock count: this exception is really thrown during execution of the query
    when(dataSource.getConnection()).thenThrow(
        new DataAccessException(sqlException.getMessage(), sqlException));

    assertThat(schema.hasPreAggregatedDependencies).isFalse();
  }

  @Test void hasRemoteServiceName_falseWhenKnownSQLState() throws SQLException {
    SQLSyntaxErrorException sqlException = new SQLSyntaxErrorException(
      "Unknown column 'zipkin_spans.remote_serviceName' in 'field list'",
      "42S22", 1054);

    // cheats to lower mock count: this exception is really thrown during execution of the query
    when(dataSource.getConnection()).thenThrow(
      new DataAccessException(sqlException.getMessage(), sqlException));

    assertThat(schema.hasRemoteServiceName).isFalse();
  }
}
