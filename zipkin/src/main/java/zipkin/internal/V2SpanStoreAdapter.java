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
package zipkin.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import zipkin.DependencyLink;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.Callback;
import zipkin2.Call;
import zipkin2.Call.Mapper;
import zipkin2.Span;
import zipkin2.internal.Platform;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.SpanStore;

import static zipkin.internal.GroupByTraceId.TRACE_DESCENDING;
import static zipkin.internal.Util.sortedList;
import static zipkin.internal.Util.toLowerHex;
import static zipkin.internal.V2SpanConverter.toSpans;

final class V2SpanStoreAdapter implements zipkin.storage.SpanStore, AsyncSpanStore {
  final SpanStore delegate;

  V2SpanStoreAdapter(SpanStore delegate) {
    this.delegate = delegate;
  }

  @Override public List<List<zipkin.Span>> getTraces(zipkin.storage.QueryRequest request) {
    try {
      return getTracesCall(request).execute();
    } catch (IOException e) {
      throw Platform.get().uncheckedIOException(e);
    }
  }

  @Override
  public void getTraces(zipkin.storage.QueryRequest request,
    Callback<List<List<zipkin.Span>>> callback) {
    getTracesCall(request).enqueue(new V2CallbackAdapter<>(callback));
  }

  Call<List<List<zipkin.Span>>> getTracesCall(zipkin.storage.QueryRequest v1Request) {
    return delegate.getTraces(convertRequest(v1Request)).map(GetTracesMapper.INSTANCE);
  }

  @Nullable @Override public List<zipkin.Span> getTrace(long traceIdHigh, long traceIdLow) {
    try {
      return getTraceCall(traceIdHigh, traceIdLow).execute();
    } catch (IOException e) {
      throw Platform.get().uncheckedIOException(e);
    }
  }

  @Override
  public void getTrace(long traceIdHigh, long traceIdLow, Callback<List<zipkin.Span>> callback) {
    getTraceCall(traceIdHigh, traceIdLow).enqueue(new V2CallbackAdapter<>(callback));
  }

  Call<List<zipkin.Span>> getTraceCall(long traceIdHigh, long traceIdLow) {
    return delegate.getTrace(toLowerHex(traceIdHigh, traceIdLow)).map(GetTraceMapper.INSTANCE);
  }

  @Nullable @Override public List<zipkin.Span> getRawTrace(long traceIdHigh, long traceIdLow) {
    try {
      return getRawTraceCall(traceIdHigh, traceIdLow).execute();
    } catch (IOException e) {
      throw Platform.get().uncheckedIOException(e);
    }
  }

  @Override
  public void getRawTrace(long traceIdHigh, long traceIdLow,
    Callback<List<zipkin.Span>> callback) {
    getRawTraceCall(traceIdHigh, traceIdLow).enqueue(new V2CallbackAdapter<>(callback));
  }

  Call<List<zipkin.Span>> getRawTraceCall(long traceIdHigh, long traceIdLow) {
    return delegate.getTrace(toLowerHex(traceIdHigh, traceIdLow)).map(GetRawTraceMapper.INSTANCE);
  }

  @Override public List<String> getServiceNames() {
    try {
      return sortedList(delegate.getServiceNames().execute());
    } catch (IOException e) {
      throw Platform.get().uncheckedIOException(e);
    }
  }

  @Override public void getServiceNames(Callback<List<String>> callback) {
    delegate.getServiceNames().enqueue(new V2CallbackAdapter<>(callback));
  }

  @Override public List<String> getSpanNames(String serviceName) {
    try {
      return sortedList(delegate.getSpanNames(serviceName).execute());
    } catch (IOException e) {
      throw Platform.get().uncheckedIOException(e);
    }
  }

  @Override public void getSpanNames(String serviceName, Callback<List<String>> callback) {
    delegate.getSpanNames(serviceName).enqueue(new V2CallbackAdapter<>(callback));
  }

  @Override public List<DependencyLink> getDependencies(long endTs, @Nullable Long lookback) {
    try {
      return getDependenciesCall(endTs, lookback).execute();
    } catch (IOException e) {
      throw Platform.get().uncheckedIOException(e);
    }
  }

  @Override public void getDependencies(long endTs, @Nullable Long lookback,
    Callback<List<DependencyLink>> callback) {
    getDependenciesCall(endTs, lookback).enqueue(new V2CallbackAdapter<>(callback));
  }

  Call<List<DependencyLink>> getDependenciesCall(long endTs, @Nullable Long lookback) {
    return delegate.getDependencies(endTs, lookback != null ? lookback : endTs)
      .map(ToLinks.INSTANCE);
  }

  enum ToLinks implements Call.Mapper<List<zipkin2.DependencyLink>, List<DependencyLink>> {
    INSTANCE;

    @Override public List<DependencyLink> map(List<zipkin2.DependencyLink> input) {
      return V2SpanConverter.toLinks(input);
    }

    @Override public String toString() {
      return "V2SpanConverter::toLinks";
    }
  }

  @Nullable @Override public List<zipkin.Span> getTrace(long traceId) {
    return getTrace(0L, traceId);
  }

  @Override public void getTrace(long id, Callback<List<zipkin.Span>> callback) {
    getTrace(0L, id, callback);
  }

  @Nullable @Override public List<zipkin.Span> getRawTrace(long traceId) {
    return getRawTrace(0L, traceId);
  }

  @Override public void getRawTrace(long traceId, Callback<List<zipkin.Span>> callback) {
    getRawTrace(0L, traceId, callback);
  }

  enum GetTracesMapper implements Mapper<List<List<Span>>, List<List<zipkin.Span>>> {
    INSTANCE;

    @Override public List<List<zipkin.Span>> map(List<List<Span>> trace2s) {
      if (trace2s.isEmpty()) return Collections.emptyList();
      int length = trace2s.size();
      List<List<zipkin.Span>> trace1s = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        trace1s.add(CorrectForClockSkew.apply(MergeById.apply(toSpans(trace2s.get(i)))));
      }
      Collections.sort(trace1s, TRACE_DESCENDING);
      return trace1s;
    }

    @Override public String toString() {
      return "GetTraces";
    }
  }

  enum GetTraceMapper implements Mapper<List<Span>, List<zipkin.Span>> {
    INSTANCE;

    @Override public List<zipkin.Span> map(List<Span> spans) {
      List<zipkin.Span> span1s = CorrectForClockSkew.apply(MergeById.apply(toSpans(spans)));
      return (span1s.isEmpty()) ? null : span1s;
    }

    @Override public String toString() {
      return "GetTrace";
    }
  }

  enum GetRawTraceMapper implements Mapper<List<Span>, List<zipkin.Span>> {
    INSTANCE;

    @Override public List<zipkin.Span> map(List<Span> spans) {
      List<zipkin.Span> span1s = toSpans(spans);
      return (span1s.isEmpty()) ? null : span1s;
    }

    @Override public String toString() {
      return "V2SpanConverter::toSpans";
    }
  }

  static QueryRequest convertRequest(zipkin.storage.QueryRequest v1Request) {
    return QueryRequest.newBuilder()
      .serviceName(v1Request.serviceName)
      .spanName(v1Request.spanName)
      .parseAnnotationQuery(v1Request.toAnnotationQuery())
      .minDuration(v1Request.minDuration)
      .maxDuration(v1Request.maxDuration)
      .endTs(v1Request.endTs)
      .lookback(v1Request.lookback)
      .limit(v1Request.limit).build();
  }
}
