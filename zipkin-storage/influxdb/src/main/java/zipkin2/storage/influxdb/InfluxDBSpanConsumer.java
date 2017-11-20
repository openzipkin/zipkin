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
package zipkin2.storage.influxdb;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.influxdb.dto.Point;
import zipkin2.Annotation;
import zipkin2.Call;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.storage.SpanConsumer;
import org.influxdb.dto.BatchPoints;

final class InfluxDBSpanConsumer implements SpanConsumer {
  final InfluxDBStorage storage;

  InfluxDBSpanConsumer(InfluxDBStorage influxDB) {
    this.storage = influxDB;
  }

  @Override
  public Call<Void> accept(List<Span> spans) {
    if (spans.isEmpty()) return Call.create(null);
    
    BatchPoints batch = BatchPoints
      .database(storage.database())
      .retentionPolicy(storage.retentionPolicy())
      .build();
    for (Span span : spans) {
      Point point = Point
        .measurement(storage.measurement())
        .tag("trace_id", span.traceId())
        .tag("id", span.id())
        .tag("parent_id", span.parentId() == null ? span.id() : span.parentId())
        .tag("name", span.name())
        .tag("service_name", serviceName(span))
        .addField("duration_ns", span.duration() * 1000)
        .time(span.timestamp(), TimeUnit.MICROSECONDS)
        .build();
      batch.point(point);

      for (Annotation anno : span.annotations()) {
        Point annoPoint = Point
          .measurement(storage.measurement())
          .tag("trace_id", span.traceId())
          .tag("id", span.id())
          .tag("parent_id", span.parentId() == null ? span.id() : span.parentId())
          .tag("name", span.name())
          .tag("service_name", serviceName(span))
          .tag("endpoint_host", host(span))
          .tag("annotation", anno.value())
          .addField("duration_ns", span.duration() * 1000)
          .time(span.timestamp(), TimeUnit.MICROSECONDS)
          .build();
        batch.point(annoPoint);
      }

      if (span.tags() != null) {
        for (Map.Entry<String, String> tag : span.tags().entrySet()) {
          Point taggedPoint = Point
            .measurement(storage.measurement())
            .tag("trace_id", span.traceId())
            .tag("id", span.id())
            .tag("parent_id", span.parentId() == null ? span.id() : span.parentId())
            .tag("name", span.name())
            .tag("service_name", serviceName(span))
            .tag("annotation_key", tag.getKey())
            .tag("annotation", tag.getValue())
            .tag("endpoint_host", host(span))
            .addField("duration_ns", span.duration() * 1000)
            .time(span.timestamp(), TimeUnit.MICROSECONDS)
            .build();
          batch.point(taggedPoint);
        }
      }
    }
    storage.get().write(batch);
    return Call.create(null);
  }

  private String serviceName(Span span) {
    String srv = "unknown";
    if (span.remoteServiceName() != null) {
      srv = span.remoteServiceName();
    } else if (span.localServiceName() != null) {
      srv = span.localServiceName();
    }
    return srv;
  }

  private String host(Span span) {
    String addr = "0.0.0.0";
    Endpoint ep = span.remoteEndpoint() == null ?
      span.localEndpoint() == null
        ? null
        : span.localEndpoint()
      : span.remoteEndpoint();

    if (ep == null) {
      return addr;
    }

    addr = ep.ipv4() == null ? ep.ipv6() == null ? addr : ep.ipv6() : ep.ipv4();
    return ep.port() == null ? addr : String.format("%s:%d", addr, ep.port());
  }
}
