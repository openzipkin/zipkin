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
package zipkin2.autoconfigure.server.grpc;

import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin2.server.grpc.GrpcCollector;
import zipkin2.server.grpc.GrpcCollectorType;

@ConfigurationProperties("zipkin.collector.grpc")
class ZipkinGrpcServerProperties {
  private int port;
  private GrpcCollectorType type = GrpcCollectorType.ARMERIA;

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public GrpcCollectorType getType() {
    return type;
  }

  public void setType(GrpcCollectorType type) {
    this.type = type;
  }

  public GrpcCollector.Builder toBuilder() {
    return GrpcCollector.builder()
      .type(type)
      .port(port);
  }

}
