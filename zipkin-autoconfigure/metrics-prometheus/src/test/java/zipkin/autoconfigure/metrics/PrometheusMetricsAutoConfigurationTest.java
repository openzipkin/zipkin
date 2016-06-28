/**
 * Copyright 2015-2016 The OpenZipkin Authors
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

import org.junit.Test;
import org.springframework.boot.actuate.endpoint.PublicMetrics;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class PrometheusMetricsAutoConfigurationTest {

    @Test
    public void correctHttpResponse() throws Exception {
        PublicMetrics publicMetrics = () -> Collections.singleton(new Metric<Number>("mem.free", 1024));
        ResponseEntity<String> response = responseForMetrics(publicMetrics);

        assertThat(response.getStatusCode(), equalTo(HttpStatus.OK));
        assertThat(response.getHeaders().getContentType().toString(),
                equalTo("text/plain;version=0.0.4;charset=utf-8"));
    }

    @Test
    public void defaultsToGauge() throws Exception {

        PublicMetrics publicMetrics = () -> Collections.singleton(new Metric<Number>("mem.free", 1024));
        ResponseEntity<String> response = responseForMetrics(publicMetrics);

        String body = response.getBody();

        assertThat(body, equalTo(
                "#TYPE mem_free gauge\n" +
                        "#HELP mem_free mem_free\n" +
                        "mem_free 1024.0\n"));
    }

    @Test
    public void detectsCounters() throws Exception {

        PublicMetrics publicMetrics = () -> Collections.singleton(new Metric<Number>("counter_mem.free", 1024));
        ResponseEntity<String> response = responseForMetrics(publicMetrics);

        String body = response.getBody();

        assertThat(body, equalTo(
                "#TYPE mem_free counter\n" +
                        "#HELP mem_free mem_free\n" +
                        "mem_free 1024.0\n"));
    }

    @Test
    public void detectsGauges() throws Exception {

        PublicMetrics publicMetrics = () -> Collections.singleton(new Metric<Number>("gauge_mem.free", 1024));
        ResponseEntity<String> response = responseForMetrics(publicMetrics);

        String body = response.getBody();

        assertThat(body, equalTo(
                "#TYPE mem_free gauge\n" +
                        "#HELP mem_free mem_free\n" +
                        "mem_free 1024.0\n"));
    }


    private ResponseEntity<String> responseForMetrics(PublicMetrics publicMetrics) {
        PrometheusMetricsAutoConfiguration pmc = new PrometheusMetricsAutoConfiguration(Collections.singleton(publicMetrics));

        return pmc.prometheusMetrics();
    }
}
