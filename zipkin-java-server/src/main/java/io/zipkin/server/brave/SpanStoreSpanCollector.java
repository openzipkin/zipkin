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
  private BlockingQueue<com.twitter.zipkin.gen.Span> queue = new LinkedBlockingQueue<>();
  private int limit = 200;

  public SpanStoreSpanCollector(SpanStore spanStore) {
    this.spanStore = spanStore;
  }

  @Override
  public void collect(com.twitter.zipkin.gen.Span span) {
    queue.offer(span);
    if (queue.size() >= limit) {
      flush();
    }
  }

  @Override
  public void flush() {
    List<Span> spans = new ArrayList<>(queue.size());
    while (!queue.isEmpty()) {
      com.twitter.zipkin.gen.Span span = queue.poll();
      if (span != null) {
        spans.add(convert(span));
      }
    }
    this.spanStore.accept(spans);
  }

  private Span convert(com.twitter.zipkin.gen.Span span) {
    Span.Builder builder = new Span.Builder();
    long parent = span.getParent_id();
    builder.name(span.getName())
        .id(span.getId())
        .parentId(parent == 0 ? null : parent)
        .traceId(span.getTrace_id())
        .debug(span.isDebug());
    List<com.twitter.zipkin.gen.Annotation> annotations = span.getAnnotations();
    if (annotations != null) {
      for (com.twitter.zipkin.gen.Annotation annotation : annotations) {
        builder.addAnnotation(convert(annotation));
      }
    }
    List<com.twitter.zipkin.gen.BinaryAnnotation> binaries = span.getBinary_annotations();
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
        .timestamp(annotation.getTimestamp())
        .value(annotation.getValue())
        .endpoint(convert(annotation.getHost()))
        .build();
  }

  private static Endpoint convert(com.twitter.zipkin.gen.Endpoint endpoint) {
    return new Endpoint.Builder()
        .serviceName(endpoint.getService_name())
        .port(endpoint.getPort())
        .ipv4(endpoint.getIpv4()).build();
  }

  private static BinaryAnnotation convert(com.twitter.zipkin.gen.BinaryAnnotation annotation) {
    return new BinaryAnnotation.Builder()
        .key(annotation.getKey())
        .value(annotation.getValue())
        .type(convert(annotation.getAnnotation_type()))
        .endpoint(convert(annotation.getHost()))
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
