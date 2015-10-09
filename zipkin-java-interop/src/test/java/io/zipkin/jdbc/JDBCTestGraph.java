/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.jdbc;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import io.zipkin.internal.Nullable;
import org.jooq.conf.Settings;
import org.junit.AssumptionViolatedException;

import static io.zipkin.internal.Util.envOr;

final class JDBCTestGraph {

  final JDBCSpanStore spanStore;

  JDBCTestGraph() {
    String mysqlUrl = mysqlUrlFromEnv();
    if (mysqlUrl == null) {
      throw new AssumptionViolatedException("Minimally, the environment variable MYSQL_USER must be set");
    }
    MysqlDataSource dataSource = new MysqlDataSource();
    dataSource.setURL(mysqlUrl);
    spanStore = new JDBCSpanStore(dataSource, new Settings().withRenderSchema(false), null);
  }

  @Nullable
  public static String mysqlUrlFromEnv() {
    if (System.getenv("MYSQL_USER") == null) return null;
    String mysqlHost = envOr("MYSQL_HOST", "localhost");
    int mysqlPort = envOr("MYSQL_TCP_PORT", 3306);
    String mysqlUser = envOr("MYSQL_USER", "");
    String mysqlPass = envOr("MYSQL_PASS", "");

    return String.format("jdbc:mysql://%s:%s/zipkin?user=%s&password=%s&autoReconnect=true",
        mysqlHost, mysqlPort, mysqlUser, mysqlPass);
  }
}
