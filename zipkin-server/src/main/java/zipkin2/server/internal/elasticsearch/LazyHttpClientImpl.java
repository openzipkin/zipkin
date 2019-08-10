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

import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.HttpClient;
import com.linecorp.armeria.client.endpoint.DynamicEndpointGroup;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.client.endpoint.EndpointGroupRegistry;
import com.linecorp.armeria.client.endpoint.StaticEndpointGroup;
import com.linecorp.armeria.client.endpoint.healthcheck.HealthCheckedEndpointGroup;
import com.linecorp.armeria.client.metric.MetricCollectingClient;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.common.metric.MeterIdPrefixFunction;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import zipkin2.elasticsearch.ElasticsearchStorage.LazyHttpClient;
import zipkin2.elasticsearch.internal.client.HttpCall;

import static com.linecorp.armeria.client.endpoint.EndpointSelectionStrategy.ROUND_ROBIN;

final class LazyHttpClientImpl implements LazyHttpClient {
  final HttpClientFactory factory;
  final SessionProtocol protocol;
  final Supplier<EndpointGroup> initialEndpoints;
  final ZipkinElasticsearchStorageProperties.HealthCheck healthCheck;
  final int timeoutMillis;
  final MeterRegistry meterRegistry;

  volatile HttpClient result;

  LazyHttpClientImpl(HttpClientFactory factory, SessionProtocol protocol,
    Supplier<EndpointGroup> initialEndpoints, ZipkinElasticsearchStorageProperties es,
    MeterRegistry meterRegistry) {
    this.factory = factory;
    this.protocol = protocol;
    this.initialEndpoints = initialEndpoints;
    this.healthCheck = es.getHealthCheck();
    timeoutMillis = es.getTimeout();
    this.meterRegistry = meterRegistry;
  }

  @Override public void close() {
    EndpointGroup endpointGroup = EndpointGroupRegistry.get("elasticsearch");
    if (endpointGroup != null) {
      endpointGroup.close();
      EndpointGroupRegistry.unregister("elasticsearch");
    }
  }

  @Override public HttpClient get() {
    if (result == null) {
      synchronized (this) {
        if (result == null) {
          result = factory.apply(getEndpoint());
        }
      }
    }
    return result;
  }

  Endpoint getEndpoint() {
    EndpointGroup endpointGroup = initialEndpoints.get();
    if (endpointGroup instanceof StaticEndpointGroup && endpointGroup.endpoints().size() == 1) {
      // Just one non-domain URL, can connect directly without enabling load balancing.
      return endpointGroup.endpoints().get(0);
    }

    if (endpointGroup instanceof DynamicEndpointGroup) {
      try {
        // Since we aren't holding up server startup, or sitting on the event loop, it is ok to
        // block. The alternative is round-robin, which could be unlucky and hit a bad node first.
        //
        // We are blocking up to the connection timeout which should be enough time for any DNS
        // resolution that hasn't happened yet to finish.
        endpointGroup.awaitInitialEndpoints(timeoutMillis, TimeUnit.MILLISECONDS);
      } catch (Exception e) {
        // We'll try again next time around.
        throw new IllegalStateException("couldn't connect any of " + endpointGroup.endpoints(), e);
      }
    }

    if (healthCheck.isEnabled()) endpointGroup = decorateHealthCheck(endpointGroup);

    // TODO: why must we do this instead of using direct type references.
    // The static factory is concerning. https://github.com/line/armeria/issues/1084
    EndpointGroupRegistry.register("elasticsearch", endpointGroup, ROUND_ROBIN);
    return Endpoint.ofGroup("elasticsearch");
  }

  // Enables health-checking of an endpoint group, so we only send requests to endpoints that are
  // up.
  HealthCheckedEndpointGroup decorateHealthCheck(EndpointGroup endpointGroup) {
    HealthCheckedEndpointGroup healthChecked =
      HealthCheckedEndpointGroup.builder(endpointGroup, "/_cluster/health")
        .protocol(protocol)
        .useGet(true)
        .clientFactory(factory.delegate)
        .withClientOptions(options -> {
          factory.configureOptionsExceptLogging(options);
          options.decorator(MetricCollectingClient.newDecorator(
            MeterIdPrefixFunction.ofDefault("elasticsearch-healthcheck")));
          options.decorator((delegate, ctx, req) -> {
            ctx.attr(HttpCall.NAME).set("health-check");
            return delegate.execute(ctx, req);
          });
          return options;
        })
        .retryInterval(healthCheck.getInterval())
        .build();
    healthChecked.newMeterBinder("elasticsearch").bindTo(meterRegistry);

    // Since we aren't holding up server startup, or sitting on the event loop, it is ok to block.
    // The alternative is round-robin, which could be unlucky and hit a bad node first.
    //
    // We are blocking up to the connection timeout which should be enough time for a health check
    // to respond.
    try {
      healthChecked.awaitInitialEndpoints(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      healthChecked.close(); // we'll recreate it the next time around.
      throw new IllegalStateException("couldn't connect any of " + endpointGroup.endpoints(), e);
    }
    return healthChecked;
  }

  @Override public final String toString() {
    return initialEndpoints.toString();
  }
}
