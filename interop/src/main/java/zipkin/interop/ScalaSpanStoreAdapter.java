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
package zipkin.interop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twitter.util.Future;
import com.twitter.zipkin.common.Span;
import com.twitter.zipkin.conversions.thrift$;
import com.twitter.zipkin.json.ZipkinJson$;
import com.twitter.zipkin.storage.QueryRequest;
import java.util.ArrayList;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TMemoryBuffer;
import scala.Tuple2;
import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.Seq;
import scala.collection.immutable.List;
import scala.runtime.BoxedUnit;
import zipkin.Codec;
import zipkin.SpanStore;
import zipkin.internal.Nullable;

import static java.util.stream.Collectors.toList;

/**
 * Adapts {@link SpanStore} to a scala {@link com.twitter.zipkin.storage.SpanStore} in order to test
 * against its {@link com.twitter.zipkin.storage.SpanStoreSpec} for interoperability reasons.
 *
 * <p/> This implementation uses thrift TBinaryProtocol to ensure structures are compatible.
 */
public final class ScalaSpanStoreAdapter extends com.twitter.zipkin.storage.SpanStore {
  private final SpanStore spanStore;

  public ScalaSpanStoreAdapter(SpanStore spanStore) {
    this.spanStore = spanStore;
  }

  @Override
  public Future<Seq<List<Span>>> getTraces(QueryRequest input) {
    zipkin.QueryRequest.Builder request = new zipkin.QueryRequest.Builder(input.serviceName())
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
    return toSeqFuture(spanStore.getTraces(request.build()));
  }

  @Override
  public Future<Seq<List<Span>>> getTracesByIds(Seq<Object> input) {
    java.util.List<Long> traceIds = JavaConversions.asJavaCollection(input).stream()
        .map(o -> Long.valueOf(o.toString())).collect(toList());
    return toSeqFuture(spanStore.getTracesByIds(traceIds));
  }

  private static Future<Seq<List<Span>>> toSeqFuture(java.util.List<java.util.List<zipkin.Span>> traces) {
    ArrayList<List<Span>> result = new ArrayList<>(traces.size());
    for (java.util.List<zipkin.Span> trace : traces) {
      java.util.List<Span> spans = convert(trace);
      result.add(JavaConversions.asScalaBuffer(spans).toList());
    }
    return Future.value(JavaConversions.asScalaBuffer(result));
  }

  @Override
  public Future<Seq<String>> getAllServiceNames() {
    return Future.value(JavaConversions.asScalaBuffer(spanStore.getServiceNames()).seq());
  }

  @Override
  public Future<Seq<String>> getSpanNames(String service) {
    return Future.value(JavaConversions.asScalaBuffer(spanStore.getSpanNames(service)).seq());
  }

  @Override
  public Future<BoxedUnit> apply(Seq<Span> input) {
    spanStore.accept(ScalaSpanStoreAdapter.invert(input).iterator());
    return Future.Unit();
  }

  @Override
  public void close() {
    // noop
  }

  @Nullable
  private static java.util.List<Span> convert(java.util.List<zipkin.Span> input) {
    byte[] bytes = Codec.THRIFT.writeSpans(input);
    try {
      List<Span> read = thrift$.MODULE$.thriftListToSpans(bytes);
      return JavaConversions.seqAsJavaList(read);
    } catch (RuntimeException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Nullable
  private static java.util.List<zipkin.Span> invert(Seq<Span> input) {
    try {
      TMemoryBuffer transport = new TMemoryBuffer(0);
      TBinaryProtocol oproto = new TBinaryProtocol(transport);
      oproto.writeListBegin(new TList(TType.STRUCT, input.size()));
      Iterator<Span> iterator = input.iterator();
      while (iterator.hasNext()) {
        com.twitter.zipkin.thriftscala.Span thriftSpan =
            thrift$.MODULE$.spanToThriftSpan(iterator.next()).toThrift();
        thriftSpan.write(oproto);
      }
      oproto.writeListEnd();
      byte[] bytes = transport.getArray();
      return Codec.THRIFT.readSpans(bytes);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
}
