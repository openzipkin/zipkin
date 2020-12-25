/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mariadb.jdbc.MariaDbDataSource;
import org.opentest4j.TestAbortedException;
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
    LOGGER.info("Using hostPort " + host() + ":" + port());

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
      dataSource = new MariaDbDataSource(host(), port(), "zipkin");
      dataSource.setUser("zipkin");
      dataSource.setPassword("zipkin");
      dataSource.setProperties("autoReconnect=true&useUnicode=yes&characterEncoding=UTF-8");
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
      super(parse("ghcr.io/openzipkin/zipkin-mysql:2.23.2"));
      if ("true".equals(System.getProperty("docker.skip"))) {
        throw new TestAbortedException("${docker.skip} == true");
      }
      waitStrategy = Wait.forHealthcheck();
      withLogConsumer(new Slf4jLogConsumer(LOGGER));
    }
  }
}
