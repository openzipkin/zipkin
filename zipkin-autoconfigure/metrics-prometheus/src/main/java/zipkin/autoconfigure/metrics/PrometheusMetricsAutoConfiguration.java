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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.PublicMetrics;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/prometheus")
public class PrometheusMetricsAutoConfiguration {

    private static final Pattern SANITIZE_PREFIX_PATTERN = Pattern.compile("^[^a-zA-Z_]");
    private static final Pattern SANITIZE_BODY_PATTERN = Pattern.compile("[^a-zA-Z0-9_]");

    private Collection<PublicMetrics> publicMetrics;

    @Autowired
    public PrometheusMetricsAutoConfiguration(final Collection<PublicMetrics> publicMetrics) {
        this.publicMetrics = publicMetrics;
    }

    private static String sanitizeMetricName(String metricName) {
        return SANITIZE_BODY_PATTERN.matcher(
                SANITIZE_PREFIX_PATTERN.matcher(metricName).replaceFirst("_")
        ).replaceAll("_");
    }

    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<String> prometheusMetrics() {
        StringBuilder sb = new StringBuilder();

        for (PublicMetrics publicMetrics : this.publicMetrics) {
            for (Metric<?> metric : publicMetrics.metrics()) {
                final String sanitizedName = sanitizeMetricName(metric.getName());
                final String type = typeForName(sanitizedName);
                final String metricName = metricName(sanitizedName, type);
                double value = metric.getValue().doubleValue();

                sb.append(String.format("#TYPE %s %s\n", metricName, type));
                sb.append(String.format("#HELP %s %s\n", metricName, metricName));
                sb.append(String.format("%s %s\n", metricName, prometheusDouble(value)));
            }
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/plain; version=0.0.4; charset=utf-8"))
                .body(sb.toString());

    }

    private String prometheusDouble(double value) {
        if (value == Double.POSITIVE_INFINITY) {
            return "+Inf";
        } else if (value == Double.NEGATIVE_INFINITY) {
            return "-Inf";
        } else {
            return String.valueOf(value);
        }
    }

    private String metricName(String name, String type) {
        switch (type) {
            case "counter":
                return name.replaceFirst("^counter_", "");
            case "gauge":
                return name.replaceFirst("^gauge_", "");
            default:
                return name;
        }
    }

    private String typeForName(String name) {
        if (name.startsWith("gauge")) {
            return "gauge";
        }
        if (name.startsWith("counter")) {
            return "counter";
        }

        return "gauge";
    }
}
