# Prometheus Metrics

Exposes [Spring Actuator metrics](http://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-metrics.html)
on `/prometheus` using the Prometheus exposition [text format version 0.0.4](https://prometheus.io/docs/instrumenting/exposition_formats/).

All metrics defaults to the `gauge` type, unless a known format is specified by a prefix in the metric name. Currently the only two detected types are `gauge_` and `counter_`.

## Scrape configuration example

```yaml
  - job_name: 'zipkin'
    scrape_interval: 5s
    metrics_path: '/prometheus'
    static_configs:
      - targets: ['localhost:9411']

```

