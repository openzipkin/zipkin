/**
 * Copyright 2015-2017 The OpenZipkin Authors
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

import com.github.dockerjava.api.command.InspectContainerResponse;
import org.mariadb.jdbc.MariaDbDataSource;
import org.rnorth.ducttape.unreliables.Unreliables;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.jdbc.ext.ScriptUtils;
import org.testcontainers.shaded.com.google.common.io.Resources;

import javax.script.ScriptException;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

public class ZipkinMySQLContainer extends GenericContainer<ZipkinMySQLContainer> {

  MariaDbDataSource dataSource;

  public ZipkinMySQLContainer(String version) {
    super("openzipkin/zipkin-mysql:" + version);

    withExposedPorts(3306);

    setWaitStrategy(new AbstractWaitStrategy() {
      @Override
      protected void waitUntilReady() {
        Unreliables.retryUntilTrue(1, TimeUnit.MINUTES, () -> {
          if (!container.isRunning()) {
            throw new ContainerLaunchException("Container failed to start");
          }

          try (Connection connection = dataSource.getConnection()) {
            return connection.createStatement().execute("SELECT 1");
          }
        });
      }
    });
  }

  public MariaDbDataSource getDataSource() {
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
      for (String script : scripts) {
        URL scriptURL = Resources.getResource(script);
        String statements = Resources.toString(scriptURL, Charset.defaultCharset());
        ScriptUtils.executeSqlScript(connection, script, statements);
      }
    } catch (SQLException | IOException | ScriptException e) {
      throw new RuntimeException(e);
    }
  }
}
