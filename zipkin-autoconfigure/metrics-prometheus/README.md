# Prometheus Metrics

Exposes [Spring Actuator metrics](http://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-metrics.html)
on `/prometheus` using the Prometheus exposition [text format version 0.0.4](https://prometheus.io/docs/instrumenting/exposition_formats/).

## Scrape configuration example

```yaml
  - job_name: 'zipkin'
    scrape_interval: 5s
    metrics_path: '/prometheus'
    static_configs:
      - targets: ['localhost:9411']
    metric_relabel_configs:
      # Response code count
      - source_labels: [__name__]
        regex: '^status_(\d+)_(.*)$'
        replacement: '${1}'
        target_label: status
      - source_labels: [__name__]
        regex: '^status_(\d+)_(.*)$'
        replacement: '${2}'
        target_label: path
      - source_labels: [__name__]
        regex: '^status_(\d+)_(.*)$'
        replacement: 'http_requests_total'
        target_label: __name__
```
