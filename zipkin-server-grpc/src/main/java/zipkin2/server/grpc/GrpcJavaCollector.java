/*
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
package zipkin2.server.grpc;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import zipkin2.collector.CollectorComponent;

import java.io.IOException;

public class GrpcJavaCollector extends GrpcCollector {

  private final Server server;

  public GrpcJavaCollector(BindableService spanService, int port) {
    server = ServerBuilder.forPort(port).addService(spanService).build();
  }

  /**
   * Start serving requests.
   */
  public CollectorComponent start() {
    try {
      server.start();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  /**
   * Stop serving requests and shutdown resources.
   */
  public void close() {
    server.shutdown();
  }
}
