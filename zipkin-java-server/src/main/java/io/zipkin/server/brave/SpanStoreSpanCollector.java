/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin.server.brave;

import com.github.kristofa.brave.SpanCollector;
import com.twitter.zipkin.gen.AnnotationType;
import io.zipkin.Annotation;
import io.zipkin.BinaryAnnotation;
import io.zipkin.BinaryAnnotation.Type;
import io.zipkin.Endpoint;
import io.zipkin.Span;
import io.zipkin.SpanStore;
import java.io.Flushable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A Brave {@link SpanCollector} that forwards to the local {@link SpanStore}.
 */
public class SpanStoreSpanCollector implements SpanCollector, Flushable {
  private SpanStore spanStore;
  // TODO: should we put a bound on this queue?
  // Since this is only used for internal tracing in zipkin, maybe it's ok
  private BlockingQueue<Span> queue = new LinkedBlockingQueue<>();
  private int limit = 200;

  public SpanStoreSpanCollector(SpanStore spanStore) {
    this.spanStore = spanStore;
  }

  public void collect(Span span) {
    this.queue.offer(span);
    if (this.queue.size() >= this.limit) {
      flush();
    }
  }

  @Override
  public void collect(com.twitter.zipkin.gen.Span span) {
    collect(convert(span));
  }

  @Override
  public void flush() {
    List<Span> spans = new ArrayList<>(this.queue.size());
    while (!this.queue.isEmpty()) {
      Span span = this.queue.poll();
      if (span != null) {
        spans.add(span);
      }
    }
    if (!spans.isEmpty()) {
      this.spanStore.accept(spans);
    }
  }

  private Span convert(com.twitter.zipkin.gen.Span span) {
    Span.Builder builder = new Span.Builder();
    builder.name(span.getName())
        .id(span.id)
        .parentId(span.isSetParent_id() ? span.parent_id : null)
        .traceId(span.trace_id)
        .timestamp(span.timestamp)
        .duration(span.duration)
        .debug(span.debug);
    List<com.twitter.zipkin.gen.Annotation> annotations = span.annotations;
    if (annotations != null) {
      for (com.twitter.zipkin.gen.Annotation annotation : annotations) {
        builder.addAnnotation(convert(annotation));
      }
    }
    List<com.twitter.zipkin.gen.BinaryAnnotation> binaries = span.binary_annotations;
    if (binaries != null) {
      for (com.twitter.zipkin.gen.BinaryAnnotation annotation : binaries) {
        builder.addBinaryAnnotation(convert(annotation));
      }
    }
    return builder.build();
  }

  @Override
  public void addDefaultAnnotation(String key, String value) {
  }

  @Override
  public void close() {
  }

  private static Annotation convert(com.twitter.zipkin.gen.Annotation annotation) {
    return new Annotation.Builder()
        .timestamp(annotation.timestamp)
        .value(annotation.value)
        .endpoint(convert(annotation.host))
        .build();
  }

  private static Endpoint convert(com.twitter.zipkin.gen.Endpoint endpoint) {
    return new Endpoint.Builder()
        .serviceName(endpoint.service_name)
        .port(endpoint.port)
        .ipv4(endpoint.ipv4).build();
  }

  private static BinaryAnnotation convert(com.twitter.zipkin.gen.BinaryAnnotation annotation) {
    return new BinaryAnnotation.Builder()
        .key(annotation.key)
        .value(annotation.getValue())
        .type(convert(annotation.annotation_type))
        .endpoint(convert(annotation.host))
        .build();
  }

  private static Type convert(AnnotationType type) {
    switch (type) {
      case STRING:
        return Type.STRING;
      case DOUBLE:
        return Type.DOUBLE;
      case BOOL:
        return Type.BOOL;
      case I16:
        return Type.I16;
      case I32:
        return Type.I32;
      case I64:
        return Type.I64;
      default:
        return Type.BYTES;
    }
  }
}
