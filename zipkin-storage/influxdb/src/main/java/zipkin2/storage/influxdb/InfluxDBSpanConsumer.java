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
import org.influxdb.InfluxDBException;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import zipkin2.Annotation;
import zipkin2.Call;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.storage.SpanConsumer;

final class InfluxDBSpanConsumer implements SpanConsumer {
  final InfluxDBStorage storage;

  InfluxDBSpanConsumer(InfluxDBStorage influxDB) {
    this.storage = influxDB;
    try {
      storage.get().createDatabase(storage.database());
    } catch(InfluxDBException e){}
  }

  @Override
  public Call<Void> accept(List<Span> spans) {
    if (spans.isEmpty()) return Call.create(null);

    BatchPoints batch = BatchPoints
      .database(storage.database())
      .retentionPolicy(storage.retentionPolicy())
      .build();

    for (Span span : spans) {
      Point.Builder point = Point
        .measurement(storage.measurement())
        .tag("trace_id", span.traceId())
        .tag("id", span.id());

      // When it is a root parent we set the parent_id to id to allow
      // a single query to retrieve all dependencies.
      point.tag("parent_id", span.parentId() == null ? span.id() : span.parentId());
      if (span.name() != null) point.tag("name", span.name());
      String serviceName = serviceName(span); // TODO: this is invalid, to conflate local and remote
      if (serviceName != null) point.tag("service_name", serviceName);
      if (span.timestamp() != null) point.time(span.timestamp(), TimeUnit.MICROSECONDS);
      // one field is mandatory, so initialize to zero if there's no duration
      point.addField("duration_us", span.duration() == null ? 0 : span.duration());
      batch.point(point.build());

      // add points for tags first, as they inherit the span's timestamp
      for (Map.Entry<String, String> tag : span.tags().entrySet()) {
        Point.Builder taggedPoint = Point
          .measurement(storage.measurement())
          .tag("trace_id", span.traceId())
          .tag("id", span.id());

        if (span.parentId() != null) taggedPoint.tag("parent_id", span.parentId());
        if (span.name() != null) taggedPoint.tag("name", span.name());
        String taggedServiceName = serviceName(span); // TODO: this is invalid, to conflate local and remote
        if (serviceName != null) taggedPoint.tag("service_name", taggedServiceName);
        if (span.timestamp() != null) taggedPoint.time(span.timestamp(), TimeUnit.MICROSECONDS);
        // one field is mandatory, so initialize to zero if there's no duration
        taggedPoint.addField("duration_us", span.duration() == null ? 0 : span.duration())
          .tag("annotation_key", tag.getKey())
          .tag("annotation", tag.getValue())
          .tag("endpoint_host", host(span)); // TODO: what is this for?
        batch.point(taggedPoint.build());
      }

      for (Annotation anno : span.annotations()) {
        Point.Builder annotatedPoint = Point
          .measurement(storage.measurement())
          .tag("trace_id", span.traceId())
          .tag("id", span.id());

        if (span.parentId() != null) annotatedPoint.tag("parent_id", span.parentId());
        if (span.name() != null) annotatedPoint.tag("name", span.name());
        String annoServiceName = serviceName(span); // TODO: this is invalid, to conflate local and remote
        if (serviceName != null) annotatedPoint.tag("service_name", annoServiceName);
        if (span.timestamp() != null) annotatedPoint.time(span.timestamp(), TimeUnit.MICROSECONDS);
        // one field is mandatory, so initialize to zero if there's no duration
        annotatedPoint.addField("duration_us", span.duration() == null ? 0 : span.duration());
        annotatedPoint.tag("annotation", anno.value());
        batch.point(annotatedPoint.build());
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
    // TODO: this is dangerous to conflate local and remote. add rationale or change
    Endpoint ep = span.localEndpoint() != null ? span.localEndpoint() : span.remoteEndpoint();

    if (ep == null) {
      return addr;
    }

    addr = ep.ipv4() == null ? ep.ipv6() == null ? addr : ep.ipv6() : ep.ipv4();
    return ep.port() == null ? addr : String.format("%s:%d", addr, ep.port());
  }
}
