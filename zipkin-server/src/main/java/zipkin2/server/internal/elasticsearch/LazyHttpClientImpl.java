/*
 * Copyright 2015-2020 The OpenZipkin Authors
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

import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.client.endpoint.healthcheck.HealthCheckedEndpointGroup;
import com.linecorp.armeria.client.metric.MetricCollectingClient;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.metric.MeterIdPrefixFunction;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import zipkin2.elasticsearch.ElasticsearchStorage.LazyHttpClient;

final class LazyHttpClientImpl implements LazyHttpClient {
  final HttpClientFactory factory;
  final SessionProtocol protocol;
  final Supplier<EndpointGroup> initialEndpoints;
  final ZipkinElasticsearchStorageProperties.HealthCheck healthCheck;
  final int timeoutMillis;
  final MeterRegistry meterRegistry;

  volatile WebClient result;

  LazyHttpClientImpl(HttpClientFactory factory, SessionProtocol protocol,
    Supplier<EndpointGroup> initialEndpoints, ZipkinElasticsearchStorageProperties es,
    MeterRegistry meterRegistry) {
    this.factory = factory;
    this.protocol = protocol;
    this.initialEndpoints = initialEndpoints;
    this.healthCheck = es.getHealthCheck();
    this.timeoutMillis = es.getTimeout();
    this.meterRegistry = meterRegistry;
  }

  @Override public WebClient get() {
    if (result == null) {
      synchronized (this) {
        if (result == null) {
          result = factory.apply(getEndpoint());
        }
      }
    }
    return result;
  }

  EndpointGroup getEndpoint() {
    EndpointGroup initial = initialEndpoints.get();
    // Only health-check when there are alternative endpoints. There aren't when instanceof Endpoint
    if (initial instanceof Endpoint || !healthCheck.isEnabled()) return initial;

    // Wrap the result when health checking is enabled.
    return decorateHealthCheck(initial);
  }

  // Enables health-checking of an endpoint group, so we only send requests to endpoints that are up
  HealthCheckedEndpointGroup decorateHealthCheck(EndpointGroup endpointGroup) {
    HealthCheckedEndpointGroup healthChecked =
      HealthCheckedEndpointGroup.builder(endpointGroup, "/_cluster/health")
        .protocol(protocol)
        .useGet(true)
        .clientFactory(factory.clientFactory)
        .withClientOptions(options -> {
          factory.configureHttpLogging(healthCheck.getHttpLogging(), options);
          factory.configureOptionsExceptHttpLogging(options);
          options.decorator(MetricCollectingClient.newDecorator(
            MeterIdPrefixFunction.ofDefault("elasticsearch-healthcheck")));
          options.decorator((delegate, ctx, req) -> {
            ctx.logBuilder().name("health-check");
            return delegate.execute(ctx, req);
          });
          return options;
        })
        .retryInterval(healthCheck.getInterval())
        .build();
    healthChecked.newMeterBinder("elasticsearch").bindTo(meterRegistry);
    return healthChecked;
  }

  @Override public final String toString() {
    return initialEndpoints.toString();
  }
}
