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
package io.zipkin.interop;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twitter.util.Future;
import com.twitter.zipkin.common.Span;
import com.twitter.zipkin.json.JsonSpan;
import com.twitter.zipkin.json.ZipkinJson$;
import com.twitter.zipkin.storage.QueryRequest;
import io.zipkin.Codec;
import io.zipkin.SpanStore;
import io.zipkin.internal.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Collectors;
import scala.Tuple2;
import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.Seq;
import scala.collection.immutable.List;
import scala.runtime.BoxedUnit;

import static java.util.stream.Collectors.toList;

/**
 * Adapts {@link SpanStore} to a scala {@link com.twitter.zipkin.storage.SpanStore} in order to test
 * against its {@link com.twitter.zipkin.storage.SpanStoreSpec} for interoperability reasons.
 *
 * <p/> This implementation uses json to ensure structures are compatible.
 */
public final class ScalaSpanStoreAdapter extends com.twitter.zipkin.storage.SpanStore {
  private static final ObjectMapper scalaCodec = ZipkinJson$.MODULE$;

  private final SpanStore spanStore;

  public ScalaSpanStoreAdapter(SpanStore spanStore) {
    this.spanStore = spanStore;
  }

  @Override
  public Future<Seq<List<Span>>> getTraces(QueryRequest input) {
    io.zipkin.QueryRequest.Builder request = new io.zipkin.QueryRequest.Builder(input.serviceName())
        .spanName(input.spanName().isDefined() ? input.spanName().get() : null)
        .minDuration(input.minDuration().isDefined() ? (Long) input.minDuration().get() : null)
        .maxDuration(input.maxDuration().isDefined() ? (Long) input.maxDuration().get() : null)
        .endTs(input.endTs())
        .lookback(input.lookback())
        .limit(input.limit());

    for (Iterator<String> i = input.annotations().iterator(); i.hasNext(); ) {
      request.addAnnotation(i.next());
    }

    for (Iterator<Tuple2<String, String>> i = input.binaryAnnotations().iterator(); i.hasNext(); ) {
      Tuple2<String, String> keyValue = i.next();
      request.addBinaryAnnotation(keyValue._1(), keyValue._2());
    }
    return toSeqFuture(this.spanStore.getTraces(request.build()));
  }

  @Override
  public Future<Seq<List<Span>>> getTracesByIds(Seq<Object> input) {
    java.util.List<Long> traceIds = JavaConversions.asJavaCollection(input).stream()
        .map(o -> Long.valueOf(o.toString())).collect(toList());
    return toSeqFuture(this.spanStore.getTracesByIds(traceIds));
  }

  private static Future<Seq<List<Span>>> toSeqFuture(java.util.List<java.util.List<io.zipkin.Span>> traces) {
    ArrayList<List<Span>> result = new ArrayList<>(traces.size());
    for (java.util.List<io.zipkin.Span> trace : traces) {
      java.util.List<Span> spans = convert(trace);
      result.add(JavaConversions.asScalaBuffer(spans).toList());
    }
    return Future.value(JavaConversions.asScalaBuffer(result));
  }

  @Override
  public Future<Seq<String>> getAllServiceNames() {
    return Future.value(JavaConversions.asScalaBuffer(this.spanStore.getServiceNames()).seq());
  }

  @Override
  public Future<Seq<String>> getSpanNames(String service) {
    return Future.value(JavaConversions.asScalaBuffer(this.spanStore.getSpanNames(service)).seq());
  }

  @Override
  public Future<BoxedUnit> apply(Seq<Span> input) {
    this.spanStore.accept(ScalaSpanStoreAdapter.invert(input).iterator());
    return Future.Unit();
  }

  @Override
  public void close() {
    this.spanStore.close();
  }

  @Nullable
  private static java.util.List<Span> convert(java.util.List<io.zipkin.Span> input) {
    byte[] bytes = Codec.JSON.writeSpans(input);
    try {
      TypeReference<java.util.List<JsonSpan>> ref = new TypeReference<java.util.List<JsonSpan>>(){};
      java.util.List<JsonSpan> read = scalaCodec.readValue(bytes, ref);
      return read.stream().map(JsonSpan::invert).collect(Collectors.toList());
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Nullable
  private static java.util.List<io.zipkin.Span> invert(Seq<Span> input) {
    try {
      byte[] bytes = scalaCodec.writeValueAsBytes(input);
      return Codec.JSON.readSpans(bytes);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      return null;
    }
  }
}
