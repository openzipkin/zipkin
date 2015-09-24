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

import com.facebook.swift.codec.ThriftCodecManager;
import com.facebook.swift.codec.internal.reflection.ReflectionThriftCodecFactory;
import com.facebook.swift.service.ThriftServer;
import com.facebook.swift.service.ThriftServerConfig;
import com.facebook.swift.service.ThriftServiceProcessor;
import io.zipkin.jdbc.JDBCSpanStore;
import io.zipkin.spanstore.InMemorySpanStore;
import io.zipkin.spanstore.SpanStore;
import io.zipkin.scribe.ScribeSpanConsumer;
import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import org.apache.commons.dbcp2.BasicDataSource;
import org.jooq.conf.Settings;

import static io.zipkin.internal.Util.envOr;
import static java.util.Collections.emptyList;

public final class ZipkinServer implements Closeable {

  private final int scribePort;
  private final int queryPort;
  private final SpanStore spanStore;

  private ZipkinServer(int scribePort, int queryPort, SpanStore spanStore) {
    this.scribePort = scribePort;
    this.queryPort = queryPort;
    this.spanStore = spanStore;
  }

  private ThriftServer scribe;
  private ThriftServer query;

  public void start() throws IOException {
    ScribeSpanConsumer scribe = new ScribeSpanConsumer(spanStore);
    if (scribePort == queryPort) {
      this.scribe = query = startServices(scribePort, scribe, spanStore);
    } else {
      this.scribe = startServices(scribePort, scribe);
      this.query = startServices(queryPort, spanStore);
    }
  }

  public void stop() {
    if (scribe != null) {
      scribe.close();
    }
    if (query != null) {
      query.close();
    }
  }

  private static ThriftServer startServices(int port, Object... services) throws IOException {
    ThriftServiceProcessor processor = new ThriftServiceProcessor(
        new ThriftCodecManager(new ReflectionThriftCodecFactory()), emptyList(), services);
    try (ServerSocket server = new ServerSocket(port)) {
      port = server.getLocalPort();
    }
    return new ThriftServer(processor, new ThriftServerConfig()
        .setBindAddress("localhost")
        .setPort(port)).start();
  }

  public static void main(String[] args) throws IOException, InterruptedException {

    int collectorPort = envOr("COLLECTOR_PORT", 9410);
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
      BasicDataSource datasource = new org.apache.commons.dbcp2.BasicDataSource();
      datasource.setDriverClassName("com.mysql.jdbc.Driver");
      datasource.setUrl(url);
      datasource.setMaxTotal(10);
      server = new ZipkinServer(collectorPort, queryPort, new JDBCSpanStore(datasource, new Settings()));
    } else {
      server = new ZipkinServer(collectorPort, queryPort, new InMemorySpanStore());
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
