/**
 * Copyright 2015-2018 The OpenZipkin Authors
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

import java.sql.SQLException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mariadb.jdbc.MariaDbDataSource;
import zipkin.internal.LazyCloseable;

import static org.junit.Assume.assumeTrue;
import static zipkin.internal.Util.envOr;

public class LazyMySQLStorage extends LazyCloseable<MySQLStorage> implements TestRule {

  final String version;

  ZipkinMySQLContainer container;

  LazyMySQLStorage(String version) {
    this.version = version;
  }

  @Override protected MySQLStorage compute() {
    try {
      container = new ZipkinMySQLContainer(version);
      container.start();
      System.out.println("Will use TestContainers MySQL instance");
    } catch (Exception e) {
      // Ignored
    }

    // TODO call .check()
    return computeStorageBuilder().build();
  }

  public MySQLStorage.Builder computeStorageBuilder() {
    final MariaDbDataSource dataSource;

    try {
      if (container != null && container.getDataSource() != null) {
        dataSource = container.getDataSource();
      } else {
        dataSource = new MariaDbDataSource();

        dataSource.setUser(System.getenv("MYSQL_USER"));
        assumeTrue("Minimally, the environment variable MYSQL_USER must be set", dataSource.getUser() != null);

        dataSource.setServerName(envOr("MYSQL_HOST", "localhost"));
        dataSource.setPort(envOr("MYSQL_TCP_PORT", 3306));
        dataSource.setDatabaseName(envOr("MYSQL_DB", "zipkin"));
        dataSource.setPassword(envOr("MYSQL_PASS", ""));
      }
      dataSource.setProperties("autoReconnect=true&useUnicode=yes&characterEncoding=UTF-8");
    } catch (SQLException e) {
      throw new AssertionError(e);
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
}
