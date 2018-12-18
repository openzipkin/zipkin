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

import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.grpc.GrpcSerializationFormats;
import com.linecorp.armeria.common.logging.LogLevel;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServiceWithPathMappings;
import com.linecorp.armeria.server.cors.CorsServiceBuilder;
import com.linecorp.armeria.server.grpc.GrpcServiceBuilder;
import com.linecorp.armeria.server.logging.LoggingServiceBuilder;
import io.grpc.BindableService;
import zipkin2.collector.CollectorComponent;

public class ArmeriaGrpcCollector extends GrpcCollector {

  private final Server server;

  ArmeriaGrpcCollector(BindableService service, int port) {
    // TODO Expose via configuration
    final CorsServiceBuilder corsBuilder =
      CorsServiceBuilder.forOrigin("http://foo.com")
        .allowRequestMethods(HttpMethod.POST) // Allow POST method.
        // Allow Content-type and X-GRPC-WEB headers.
        .allowRequestHeaders(HttpHeaderNames.CONTENT_TYPE,
          HttpHeaderNames.of("X-GRPC-WEB"));


    // TODO Expose formats via configuration
    ServiceWithPathMappings<HttpRequest, HttpResponse> grpcService = new GrpcServiceBuilder()
      .supportedSerializationFormats(GrpcSerializationFormats.values())
      .unsafeWrapRequestBuffers(true)
      .addService(service)
      .build();

    server = new ServerBuilder()
      .port(port, SessionProtocol.HTTP)
      .serviceUnder("/",  grpcService.decorate(new LoggingServiceBuilder()
                    .requestLogLevel(LogLevel.INFO)
                    .successfulResponseLogLevel(LogLevel.INFO)
                    .failureResponseLogLevel(LogLevel.WARN)
                    .newDecorator()))
      .build();
  }

  /**
   * Start serving requests.
   */
  public CollectorComponent start() {
    server.start();
    return this;
  }

  /**
   * Stop serving requests and shutdown resources.
   */
  public void close() {
    server.stop();
  }

}
