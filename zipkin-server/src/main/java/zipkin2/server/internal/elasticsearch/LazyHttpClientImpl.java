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
package zipkin2.server.internal.elasticsearch;

import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.ClientOptionsBuilder;
import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.HttpClientBuilder;
import com.linecorp.armeria.client.encoding.HttpDecodingClient;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.client.endpoint.EndpointGroupRegistry;
import com.linecorp.armeria.client.endpoint.StaticEndpointGroup;
import com.linecorp.armeria.client.endpoint.healthcheck.HttpHealthCheckedEndpointGroup;
import com.linecorp.armeria.client.endpoint.healthcheck.HttpHealthCheckedEndpointGroupBuilder;
import com.linecorp.armeria.client.logging.LoggingClientBuilder;
import com.linecorp.armeria.common.HttpHeaders;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.logging.LogLevel;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import zipkin2.elasticsearch.ElasticsearchStorage.LazyHttpClient;
import zipkin2.server.internal.elasticsearch.ZipkinElasticsearchStorageProperties.HttpLogging;

import static com.linecorp.armeria.client.endpoint.EndpointSelectionStrategy.ROUND_ROBIN;

final class LazyHttpClientImpl implements LazyHttpClient {
  final SessionProtocol protocol;
  final Supplier<EndpointGroup> initialEndpoints;
  /**
   * Customizes the {@link HttpClientBuilder} used when connecting to ElasticSearch. This is used by
   * the server and tests to enable detailed logging and tweak timeouts. It is also used by
   * zipkin-aws to customize authentication of requests.
   */
  final List<Consumer<ClientOptionsBuilder>> customizers;
  final ClientFactory factory;
  final int timeout;
  final HttpLogging httpLogging;

  volatile HttpClient result;

  LazyHttpClientImpl(ClientFactory factory, SessionProtocol protocol,
    Supplier<EndpointGroup> initialEndpoints, List<Consumer<ClientOptionsBuilder>> customizers,
    int timeout, HttpLogging httpLogging) {
    this.protocol = protocol;
    this.initialEndpoints = initialEndpoints;
    this.customizers = customizers;
    this.factory = factory;
    this.timeout = timeout;
    this.httpLogging = httpLogging;
  }

  @Override public void close() {
    EndpointGroup endpointGroup = EndpointGroupRegistry.get("elasticsearch");
    if (endpointGroup != null) {
      endpointGroup.close();
      EndpointGroupRegistry.unregister("elasticsearch");
    }
    factory.close();
  }

  @Override public HttpClient get() {
    if (result == null) {
      synchronized (this) {
        if (result == null) {
          result = doInit();
        }
      }
    }
    return result;
  }

  HttpClient doInit() {
    ClientOptionsBuilder options = new ClientOptionsBuilder()
      .decorator(HttpDecodingClient.newDecorator());

    if (httpLogging != HttpLogging.NONE) {
      LoggingClientBuilder loggingBuilder = new LoggingClientBuilder()
        .requestLogLevel(LogLevel.INFO)
        .successfulResponseLogLevel(LogLevel.INFO);
      switch (httpLogging) {
        case HEADERS:
          loggingBuilder.contentSanitizer(unused -> "");
          break;
        case BASIC:
          loggingBuilder.contentSanitizer(unused -> "");
          loggingBuilder.headersSanitizer(unused -> HttpHeaders.of());
          break;
        case BODY:
        default:
          break;
      }
      options.decorator(loggingBuilder.newDecorator());
      if (httpLogging == HttpLogging.BODY) {
        options.decorator(RawContentLoggingClient::new);
      }
    }

    configureOptions(options, customizers, timeout);

    EndpointGroup endpointGroup = initialEndpoints.get();
    if (endpointGroup instanceof StaticEndpointGroup && endpointGroup.endpoints().size() == 1) {
      Endpoint endpoint = endpointGroup.endpoints().get(0);
      // Just one non-domain URL, can connect directly without enabling load balancing.
      return new HttpClientBuilder(protocol, endpoint).factory(factory)
        .options(options.build())
        .build();
    }

    HttpHealthCheckedEndpointGroup healthChecked =
      new HttpHealthCheckedEndpointGroupBuilder(endpointGroup, "/_cluster/health")
        .protocol(protocol)
        .clientFactory(factory)
        .withClientOptions(o -> {
          configureOptions(o, customizers, timeout);
          return o;
        }).build();

    // Since we aren't holding up server startup, or sitting on the event loop, it is ok to block.
    // The alternative is round-robin, which could be unlucky and hit a bad node first.
    //
    // We are blocking a second as this should be enough time for a health check to respond
    try {
      healthChecked.awaitInitialEndpoints(1, TimeUnit.SECONDS);
    } catch (Exception e) {
      healthChecked.close(); // we'll recreate it the next time around.
      throw new IllegalStateException("couldn't connect any of " + endpointGroup.endpoints(), e);
    }

    EndpointGroupRegistry.register("elasticsearch", healthChecked, ROUND_ROBIN);

    return new HttpClientBuilder(protocol, Endpoint.ofGroup("elasticsearch"))
      .factory(factory).options(options.build()).build();
  }

  static void configureOptions(ClientOptionsBuilder options,
    List<Consumer<ClientOptionsBuilder>> clientCustomizers, int timeout) {
    options.responseTimeoutMillis(timeout).writeTimeoutMillis(timeout);
    clientCustomizers.forEach(c -> c.accept(options));
  }

  @Override public final String toString() {
    return initialEndpoints.toString();
  }
}
