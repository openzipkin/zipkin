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
package io.zipkin.query;

import com.facebook.swift.codec.ThriftCodec;
import com.facebook.swift.codec.ThriftCodecManager;
import com.twitter.util.Future;
import com.twitter.zipkin.common.Span;
import com.twitter.zipkin.conversions.thrift;
import com.twitter.zipkin.storage.IndexedTraceId;
import com.twitter.zipkin.storage.SpanStore;
import com.twitter.zipkin.thriftscala.Span$;
import io.zipkin.Annotation;
import io.zipkin.Trace;
import io.zipkin.internal.Nullable;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TMemoryBuffer;
import scala.Option;
import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.Seq;
import scala.collection.immutable.Set;
import scala.runtime.BoxedUnit;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

/**
 * Adapts {@link ZipkinQuery} to a scala/scrooge {@link com.twitter.zipkin.storage.SpanStore} in
 * order to test against its {@link com.twitter.zipkin.storage.SpanStoreSpec} for interoperability
 * reasons.
 */
public final class ZipkinQuerySpanStoreAdapter<T extends ZipkinQuery & Consumer<List<io.zipkin.Span>>> extends SpanStore {
  private static final ThriftCodec<io.zipkin.Span> spanCodec = new ThriftCodecManager().getCodec(io.zipkin.Span.class);

  private final T spanStore;

  public ZipkinQuerySpanStoreAdapter(T spanStore) {
    this.spanStore = spanStore;
  }

  @Override
  public Future<Seq<Seq<Span>>> getSpansByTraceIds(Seq<Object> traceIds) {
    final List<Long> input = new ArrayList<>(traceIds.size());
    for (Iterator<Object> i = traceIds.iterator(); i.hasNext(); input.add((Long) i.next())) ;
    return Future.value(
        JavaConversions.asScalaBuffer(spanStore.getTracesByIds(input, false).stream()
            .map(t -> JavaConversions.asScalaBuffer(
                    t.spans().stream()
                        .map(ZipkinQuerySpanStoreAdapter::convert)
                        .filter(s -> s != null)
                        .collect(Collectors.toList()))
            ).collect(Collectors.toList())));
  }

  @Override
  public Future<Seq<IndexedTraceId>> getTraceIdsByName(String serviceName, Option<String> spanName, long endTs, int limit) {
    QueryRequest request = QueryRequest.builder()
        .serviceName(serviceName)
        .spanName(spanName.isDefined() ? spanName.get() : null)
        .endTs(endTs)
        .limit(limit)
        .build();
    return indexedTraceIdFuture(spanStore.getTraces(request));
  }

  @Override
  public Future<Seq<IndexedTraceId>> getTraceIdsByAnnotation(String serviceName, String annotation, Option<ByteBuffer> value, long endTs, int limit) {
    QueryRequest request = QueryRequest.builder()
        .serviceName(serviceName)
        .annotations(value.isEmpty() ? singletonList(annotation) : emptyList())
        .binaryAnnotations(value.isDefined() ? singletonMap(annotation, new String(value.get().array(), Charset.forName("UTF-8"))) : emptyMap())
        .endTs(endTs)
        .limit(limit)
        .build();
    return indexedTraceIdFuture(spanStore.getTraces(request));
  }

  @Override
  public Future<Set<String>> getAllServiceNames() {
    return Future.value(JavaConversions.asScalaSet(spanStore.getServiceNames()).toSet());
  }

  @Override
  public Future<Set<String>> getSpanNames(String service) {
    return Future.value(JavaConversions.asScalaSet(spanStore.getSpanNames(service)).toSet());
  }

  @Override
  public Future<BoxedUnit> apply(Seq<Span> input) {
    List<io.zipkin.Span> spans = JavaConversions.asJavaCollection(input).stream()
        .map(ZipkinQuerySpanStoreAdapter::invert)
        .filter(s -> s != null)
        .collect(Collectors.toList());
    spanStore.accept(spans);
    return Future.Unit();
  }

  @Override
  public void close() {
  }

  static Future<Seq<IndexedTraceId>> indexedTraceIdFuture(List<Trace> matchingSpans) {
    return Future.value(JavaConversions.asScalaBuffer(matchingSpans.stream().map(trace -> {
      long traceId = trace.spans().get(0).traceId();
      long maxTimestamp = trace.spans().stream()
          .map(io.zipkin.Span::annotations).flatMap(List::stream)
          .mapToLong(Annotation::timestamp).max().getAsLong();
      return IndexedTraceId.apply(traceId, maxTimestamp);
    }).collect(Collectors.toList())));
  }

  @Nullable
  static Span convert(io.zipkin.Span input) {
    try {
      TMemoryBuffer transport = new TMemoryBuffer(0);
      TBinaryProtocol protocol = new TBinaryProtocol(transport);
      spanCodec.write(input, protocol);
      com.twitter.zipkin.thriftscala.Span scroogeThrift = Span$.MODULE$.decode(protocol);
      return new thrift.WrappedSpan(scroogeThrift).toSpan();
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  @Nullable
  static io.zipkin.Span invert(Span input) {
    try {
      TMemoryBuffer transport = new TMemoryBuffer(0);
      TBinaryProtocol protocol = new TBinaryProtocol(transport);
      Span$.MODULE$.encode(new thrift.ThriftSpan(input).toThrift(), protocol);
      return spanCodec.read(protocol);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
}