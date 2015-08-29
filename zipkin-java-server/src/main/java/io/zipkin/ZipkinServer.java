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
import io.zipkin.query.InMemoryZipkinQuery;
import io.zipkin.scribe.ScribeSpanConsumer;
import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;

import static java.util.Collections.emptyList;

public final class ZipkinServer implements Closeable {

  private final int scribePort;
  private final int queryPort;

  public ZipkinServer() {
    this(0, 0);
  }

  private ZipkinServer(int scribePort, int queryPort) {
    this.scribePort = scribePort;
    this.queryPort = queryPort;
  }

  InMemoryZipkinQuery mem = new InMemoryZipkinQuery();

  private ThriftServer scribe;
  private ThriftServer query;

  public void start() throws IOException {
    ScribeSpanConsumer scribe = new ScribeSpanConsumer(mem::accept);
    if (scribePort == queryPort) {
      this.scribe = query = startServices(scribePort, scribe, mem);
    } else {
      this.scribe = startServices(scribePort, scribe);
      this.query = startServices(queryPort, mem);
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
    try (ZipkinServer rule = new ZipkinServer(9410, 9411)) {
      rule.start();
      Thread.currentThread().join();
    }
  }

  @Override
  public void close() {
    stop();
  }
}
