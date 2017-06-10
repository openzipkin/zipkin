/**
 * Copyright 2015-2017 The OpenZipkin Authors
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

package zipkin.autoconfigure.metrics;

import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.spring.boot.EnablePrometheusEndpoint;
import io.prometheus.client.spring.boot.EnableSpringBootMetricsCollector;
import io.prometheus.client.spring.web.EnablePrometheusTiming;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.Filter;
import javax.servlet.ServletException;

@EnablePrometheusEndpoint
@EnableSpringBootMetricsCollector
@EnablePrometheusTiming
@Configuration
public class PrometheusMetricsAutoConfiguration {
  PrometheusMetricsAutoConfiguration() {
    DefaultExports.initialize();
  }

  @Bean
  public Filter prometheusMetricsFilter() throws ServletException {
    return new io.prometheus.client.filter.MetricsFilter("http_request_duration_seconds",
      "Response time histogram",
      0,
      null);
  }
}
