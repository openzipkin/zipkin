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
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.mariadb.jdbc.MariaDbDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assume.assumeTrue;

public class LazyMySQLStorage implements TestRule {
  static final Logger LOGGER = LoggerFactory.getLogger(LazyMySQLStorage.class);

  final String image;

  ZipkinMySQLContainer container;

  LazyMySQLStorage(String image) {
    this.image = image;
  }

  MySQLStorage storage;
  MySQLStorage get() {
    // tests don't have race conditions as they aren't run multithreaded
    if (storage != null) return storage;

    if (!"true".equals(System.getProperty("docker.skip"))) {
      try {
        container = new ZipkinMySQLContainer(image);
        container.start();
        LOGGER.info("Starting docker image " + container.getDockerImageName());
      } catch (Exception e) {
        LOGGER.warn("Couldn't start docker image " + container.getDockerImageName(), e);
      }
    } else {
      LOGGER.info("Skipping startup of docker");
    }

    // TODO call .check()
    return storage = computeStorageBuilder().build();
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

  void close() {
    try {
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

  static int envOr(String key, int fallback) {
    return System.getenv(key) != null ? Integer.parseInt(System.getenv(key)) : fallback;
  }

  static String envOr(String key, String fallback) {
    return System.getenv(key) != null ? System.getenv(key) : fallback;
  }
}
