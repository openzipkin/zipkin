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
package zipkin.jdbc;

import java.sql.SQLException;
import org.jooq.conf.Settings;
import org.junit.AssumptionViolatedException;
import org.mariadb.jdbc.MariaDbDataSource;
import zipkin.internal.Nullable;

import static zipkin.internal.Util.envOr;

final class JDBCTestGraph {

  final JDBCSpanStore spanStore;

  JDBCTestGraph() throws SQLException {
    String mysqlUrl = mysqlUrlFromEnv();
    if (mysqlUrl == null) {
      throw new AssumptionViolatedException("Minimally, the environment variable MYSQL_USER must be set");
    }
    MariaDbDataSource dataSource = new MariaDbDataSource();
    dataSource.setUrl(mysqlUrl);
    this.spanStore = new JDBCSpanStore(dataSource, new Settings().withRenderSchema(false), null);
  }

  @Nullable
  private static String mysqlUrlFromEnv() {
    if (System.getenv("MYSQL_USER") == null) return null;
    String mysqlHost = envOr("MYSQL_HOST", "localhost");
    int mysqlPort = envOr("MYSQL_TCP_PORT", 3306);
    String mysqlUser = envOr("MYSQL_USER", "");
    String mysqlPass = envOr("MYSQL_PASS", "");
    String mysqlDb  = envOr("MYSQL_DB", "zipkin");

    return String.format("jdbc:mysql://%s:%s/%s?user=%s&password=%s&autoReconnect=true",
        mysqlHost, mysqlPort, mysqlDb, mysqlUser, mysqlPass);
  }
}
