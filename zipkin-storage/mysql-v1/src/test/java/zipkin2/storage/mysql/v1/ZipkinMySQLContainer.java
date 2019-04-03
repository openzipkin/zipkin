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

import com.github.dockerjava.api.command.InspectContainerResponse;
import java.sql.Connection;
import java.sql.SQLException;
import org.mariadb.jdbc.MariaDbDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.jdbc.ContainerLessJdbcDelegate;

import static org.testcontainers.ext.ScriptUtils.runInitScript;

final class ZipkinMySQLContainer extends GenericContainer<ZipkinMySQLContainer> {

  MariaDbDataSource dataSource;

  ZipkinMySQLContainer(String version) {
    super("openzipkin/zipkin-mysql:" + version);
    withExposedPorts(3306);
  }

  MariaDbDataSource getDataSource() {
    return dataSource;
  }

  @Override protected void containerIsStarting(InspectContainerResponse containerInfo) {
    try {
      dataSource = new MariaDbDataSource(
        getContainerIpAddress(),
        getMappedPort(getExposedPorts().get(0)),
        "zipkin"
      );
      dataSource.setUser("zipkin");
      dataSource.setPassword("zipkin");
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override protected void containerIsStarted(InspectContainerResponse containerInfo) {
    String[] scripts = {
      // Drop all previously created tables in zipkin.*
      "drop_zipkin_tables.sql",

      // Populate the schema
      "mysql.sql"
    };

    try (Connection connection = dataSource.getConnection()) {
      for (String scriptPath : scripts) {
        runInitScript(new ContainerLessJdbcDelegate(connection), scriptPath);
      }
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}
