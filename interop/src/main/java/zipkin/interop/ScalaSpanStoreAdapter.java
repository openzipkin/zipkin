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

import com.twitter.util.Future;
import com.twitter.zipkin.common.Span;
import com.twitter.zipkin.conversions.thrift$;
import com.twitter.zipkin.storage.QueryRequest;
import java.util.ArrayList;
import java.util.Comparator;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TMemoryBuffer;
import scala.Function1;
import scala.Tuple2;
import scala.collection.Iterator;
import scala.collection.JavaConversions;
import scala.collection.Seq;
import scala.collection.immutable.List;
import scala.math.Ordering;
import scala.math.Ordering$;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import zipkin.AsyncSpanConsumer;
import zipkin.AsyncSpanStore;
import zipkin.Codec;
import zipkin.StorageComponent;
import zipkin.internal.Nullable;

import static zipkin.CollectorMetrics.NOOP_METRICS;
import static zipkin.CollectorSampler.ALWAYS_SAMPLE;
import static zipkin.interop.CloseAdapter.closeQuietly;

/**
 * Adapts a {@link StorageComponent} to a scala {@link com.twitter.zipkin.storage.SpanStore} in
 * order to test against its {@link com.twitter.zipkin.storage.SpanStoreSpec} for interoperability
 * reasons.
 *
 * <p>This implementation uses thrift TBinaryProtocol to ensure structures are compatible.
 */
public final class ScalaSpanStoreAdapter extends com.twitter.zipkin.storage.SpanStore {
  private final AsyncSpanStore spanStore;
  private final AsyncSpanConsumer spanConsumer;

  public ScalaSpanStoreAdapter(StorageComponent storage) {
    this.spanStore = storage.asyncSpanStore();
    this.spanConsumer = storage.asyncSpanConsumer(ALWAYS_SAMPLE, NOOP_METRICS);
  }

  @Override
  public Future<BoxedUnit> apply(Seq<Span> input) {
    VoidCallback callback = new VoidCallback();
    spanConsumer.accept(invert(input), callback);
    return callback.promise;
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
    GetTracesCallback callback = new GetTracesCallback();
    spanStore.getTraces(request.build(), callback);
    return callback.promise;
  }

  static final class GetTracesCallback
      extends CallbackWithPromise<java.util.List<java.util.List<zipkin.Span>>, Seq<List<Span>>> {
    @Override protected Seq<List<Span>> convertToScala(java.util.List<java.util.List<zipkin.Span>> input) {
      return toSeq(input);
    }
  }

  @Override
  public Future<Seq<List<Span>>> getTracesByIds(Seq<Object> input) {
    java.util.List<Future<List<Span>>> result = new ArrayList<>(input.size());
    for (Iterator<Object> traceIds = input.iterator(); traceIds.hasNext(); ) {
      GetTraceCallback callback = new GetTraceCallback();
      spanStore.getTrace(Long.valueOf(traceIds.next().toString()), callback);
      result.add(callback.promise);
    }
    return collectNotNullAndSort(result);
  }

  @Override
  public Future<Seq<Seq<Span>>> getSpansByTraceIds(Seq<Object> input) {
    java.util.List<Future<List<Span>>> result = new ArrayList<>(input.size());
    for (Iterator<Object> traceIds = input.iterator(); traceIds.hasNext(); ) {
      GetTraceCallback callback = new GetTraceCallback();
      spanStore.getRawTrace(Long.valueOf(traceIds.next().toString()), callback);
      result.add(callback.promise);
    }
    return (Future) collectNotNullAndSort(result); // a lot more code to "not sort"
  }

  @Override
  public Future<Seq<String>> getAllServiceNames() {
    ToSeqCallback<String> callback = new ToSeqCallback<>();
    spanStore.getServiceNames(callback);
    return callback.promise;
  }

  @Override
  public Future<Seq<String>> getSpanNames(String service) {
    ToSeqCallback<String> callback = new ToSeqCallback<>();
    spanStore.getSpanNames(service, callback);
    return callback.promise;
  }

  @Override
  public void close() {
    closeQuietly(spanStore);
  }

  static final class ToSeqCallback<V> extends CallbackWithPromise<java.util.List<V>, Seq<V>> {
    @Override protected Seq<V> convertToScala(java.util.List<V> input) {
      return JavaConversions.asScalaBuffer(input).seq();
    }
  }

  static final class VoidCallback extends CallbackWithPromise<Void, BoxedUnit> {
    @Override protected BoxedUnit convertToScala(Void input) {
      return BoxedUnit.UNIT;
    }
  }

  @Nullable
  static List<Span> convert(java.util.List<zipkin.Span> input) {
    if (input == null) return null;
    byte[] bytes = Codec.THRIFT.writeSpans(input);
    try {
      return thrift$.MODULE$.thriftListToSpans(bytes);
    } catch (RuntimeException e) {
      e.printStackTrace();
      return null;
    }
  }

  @Nullable
  static java.util.List<zipkin.Span> invert(Seq<Span> input) {
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

  static Future<Seq<List<Span>>> collectNotNullAndSort(java.util.List<Future<List<Span>>> result) {
    return Future.collect(JavaConversions.asScalaBuffer(result)).map(
        new AbstractFunction1<Seq<List<Span>>, Seq<List<Span>>>() {
          @Override public Seq<List<Span>> apply(Seq<List<Span>> v1) {
            return (Seq) v1.filter(NOT_NULL).toList().sorted(TRACE_DESCENDING);
          }
        });
  }

  static final Function1<List<Span>, Object> NOT_NULL = new AbstractFunction1<List<Span>, Object>() {
    @Override public Object apply(List<Span> v1) {
      return v1 != null;
    }
  };

  static final class GetTraceCallback
      extends CallbackWithPromise<java.util.List<zipkin.Span>, List<Span>> {
    @Override protected List<Span> convertToScala(java.util.List<zipkin.Span> input) {
      return convert(input);
    }
  }

  static final Ordering<List<Span>> TRACE_DESCENDING =
      Ordering$.MODULE$.comparatorToOrdering(new Comparator<List<Span>>() {
        @Override
        public int compare(List<Span> left, List<Span> right) {
          return right.apply(0).compareTo(left.apply(0));
        }
      });

  static Seq<List<Span>> toSeq(java.util.List<java.util.List<zipkin.Span>> traces) {
    ArrayList<List<Span>> result = new ArrayList<>(traces.size());
    for (java.util.List<zipkin.Span> trace : traces) {
      result.add(convert(trace).toList());
    }
    return JavaConversions.asScalaBuffer(result);
  }
}
