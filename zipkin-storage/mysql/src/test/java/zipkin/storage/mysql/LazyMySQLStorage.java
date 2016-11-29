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
package zipkin.storage.mysql;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.jdbc.ext.ScriptUtils;
import org.testcontainers.shaded.com.google.common.base.Charsets;
import org.testcontainers.shaded.com.google.common.io.Resources;
import zipkin.internal.LazyCloseable;
import zipkin.internal.Nullable;

import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;

import static zipkin.internal.Util.envOr;

public class LazyMySQLStorage extends LazyCloseable<MySQLStorage>
    implements TestRule {

  final String image;

  MySQLContainer container;

  public LazyMySQLStorage(String image) {
    this.image = image;
  }

  @Override protected MySQLStorage compute() {
    try {
      container = new MySQLContainer(image)
          .withConfigurationOverride("mysql_conf_override/");
      container.start();

      URL resource = Resources.getResource("mysql.sql");
      String sql = Resources.toString(resource, Charsets.UTF_8);
      try (Connection connection = container.createConnection("")) {
        ScriptUtils.executeSqlScript(connection, "mysql.sql", sql);
      }
      System.out.println("Will use TestContainers Elasticsearch instance");
    } catch (Exception e) {
      // Ignored
    }

    // TODO call .check()
    return computeStorageBuilder().build();
  }

  public MySQLStorage.Builder computeStorageBuilder() {
    MariaDbDataSource dataSource = new MariaDbDataSource();

    try {
      if (container != null && container.isRunning()) {
         dataSource.setUrl(container.getJdbcUrl());
         dataSource.setUserName(container.getUsername());
         dataSource.setPassword(container.getPassword());
      } else {
        String mysqlUrl = mysqlUrlFromEnv();
        if (mysqlUrl == null) {
          throw new AssumptionViolatedException(
              "Minimally, the environment variable MYSQL_USER must be set");
        }
        dataSource.setUrl(mysqlUrl);
      }
    } catch (SQLException e) {
      throw new AssumptionViolatedException(e.getMessage(), e);
    }

    return new MySQLStorage.Builder()
        .datasource(dataSource)
        .executor(Runnable::run);
  }

  @Override public void close() {
    try {
      MySQLStorage storage = maybeNull();

      if (storage != null) storage.close();
    } finally {
      if (container != null) container.stop();
    }
  }

  @Override public Statement apply(Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        get();
        try {
          base.evaluate();
        } finally {
          close();
        }
      }
    };
  }

  @Nullable
  private static String mysqlUrlFromEnv() {
    if (System.getenv("MYSQL_USER") == null) return null;
    String mysqlHost = envOr("MYSQL_HOST", "localhost");
    int mysqlPort = envOr("MYSQL_TCP_PORT", 3306);
    String mysqlUser = envOr("MYSQL_USER", "");
    String mysqlPass = envOr("MYSQL_PASS", "");
    String mysqlDb = envOr("MYSQL_DB", "zipkin");

    return String.format("jdbc:mysql://%s:%s/%s?user=%s&password=%s&autoReconnect=true&useUnicode=yes&characterEncoding=UTF-8",
        mysqlHost, mysqlPort, mysqlDb, mysqlUser, mysqlPass);
  }
}
