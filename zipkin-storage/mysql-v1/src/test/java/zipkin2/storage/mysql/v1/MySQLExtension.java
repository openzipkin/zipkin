/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mariadb.jdbc.MariaDbDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.jdbc.ContainerLessJdbcDelegate;
import zipkin2.CheckResult;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.testcontainers.ext.ScriptUtils.runInitScript;
import static org.testcontainers.utility.DockerImageName.parse;

class MySQLExtension implements BeforeAllCallback, AfterAllCallback {
  static final Logger LOGGER = LoggerFactory.getLogger(MySQLExtension.class);

  final MySQLContainer container = new MySQLContainer();

  @Override public void beforeAll(ExtensionContext context) throws Exception {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    container.start();
    LOGGER.info("Using hostPort {}:{}", host(), port());

    try (MySQLStorage result = computeStorageBuilder().build()) {
      CheckResult check = result.check();
      assumeTrue(check.ok(), () -> "Could not connect to storage, skipping test: "
        + check.error().getMessage());

      dropAndRecreateSchema(result.datasource);
    }
  }

  /**
   * MySQL doesn't auto-install schema. However, we may have changed it since the last time this
   * image was published. So, we drop and re-create the schema before running any tests.
   */
  static void dropAndRecreateSchema(DataSource datasource) throws SQLException {
    String[] scripts = {
      // Drop all previously created tables in zipkin.*
      "drop_zipkin_tables.sql",

      // Populate the schema
      "mysql.sql"
    };

    try (Connection connection = datasource.getConnection()) {
      for (String scriptPath : scripts) {
        runInitScript(new ContainerLessJdbcDelegate(connection), scriptPath);
      }
    }
  }

  @Override public void afterAll(ExtensionContext context) {
    if (context.getRequiredTestClass().getEnclosingClass() != null) {
      // Only run once in outermost scope.
      return;
    }

    container.stop();
  }

  MySQLStorage.Builder computeStorageBuilder() {
    final MariaDbDataSource dataSource;
    try {
      dataSource = new MariaDbDataSource(
        "jdbc:mariadb://%s:%s/zipkin?autoReconnect=true&useUnicode=yes&characterEncoding=UTF-8".formatted(
        host(), port()));
      dataSource.setUser("zipkin");
      dataSource.setPassword("zipkin");
    } catch (SQLException e) {
      throw new AssertionError(e);
    }

    return new MySQLStorage.Builder()
      .datasource(dataSource)
      .executor(Runnable::run);
  }

  String host() {
    return container.getHost();
  }

  int port() {
    return container.getMappedPort(3306);
  }

  // mostly waiting for https://github.com/testcontainers/testcontainers-java/issues/3537
  static final class MySQLContainer extends GenericContainer<MySQLContainer> {
    MySQLContainer() {
      super(parse("ghcr.io/openzipkin/zipkin-mysql:3.3.1"));
      addExposedPort(3306);
      waitStrategy = Wait.forHealthcheck();
      withLogConsumer(new Slf4jLogConsumer(LOGGER));
    }
  }
}
