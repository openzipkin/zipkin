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
package io.zipkin;

import com.zaxxer.hikari.HikariDataSource;
import io.zipkin.jdbc.JDBCSpanStore;
import java.io.Closeable;
import java.io.IOException;
import org.jooq.conf.Settings;

import static io.zipkin.internal.Util.envOr;

public final class ZipkinServer implements Closeable {

  private final int queryPort;
  private final SpanStore spanStore;

  private ZipkinServer(int queryPort, SpanStore spanStore) {
    this.queryPort = queryPort;
    this.spanStore = spanStore;
  }

  public void start() throws IOException {
    // TODO
  }

  public void stop() {
    // TODO
    spanStore.close();
  }

  public static void main(String[] args) throws IOException, InterruptedException {

    int queryPort = envOr("QUERY_PORT", 9411);

    final ZipkinServer server;
    if (System.getenv("MYSQL_HOST") != null) {
      String mysqlHost = System.getenv("MYSQL_HOST");
      int mysqlPort = envOr("MYSQL_TCP_PORT", 3306);
      String mysqlUser = envOr("MYSQL_USER", "");
      String mysqlPass = envOr("MYSQL_PASS", "");

      String url = String.format("jdbc:mysql://%s:%s/zipkin?user=%s&password=%s&autoReconnect=true",
          mysqlHost, mysqlPort, mysqlUser, mysqlPass);

      // TODO: replace with HikariDataSource when 2.4.2 is out
      HikariDataSource datasource = new HikariDataSource();
      datasource.setDriverClassName("com.mysql.jdbc.Driver");
      datasource.setJdbcUrl(url);
      datasource.setMaximumPoolSize(10);
      datasource.setConnectionTestQuery("SELECT '1'");
      server = new ZipkinServer(queryPort, new JDBCSpanStore(datasource, new Settings()));
    } else {
      server = new ZipkinServer(queryPort, new InMemorySpanStore());
    }
    try {
      server.start();
      Thread.currentThread().join();
    } finally {
      server.close();
    }
  }

  @Override
  public void close() {
    stop();
  }
}
