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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.Callback;
import zipkin.storage.QueryRequest;

/**
 * This makes redundant read commands, concatenating results if two answers come back, or accepting
 * one if there's an error on the other.
 */
final class LenientDoubleCallbackAsyncSpanStore implements AsyncSpanStore {
  final AsyncSpanStore left;
  final AsyncSpanStore right;

  LenientDoubleCallbackAsyncSpanStore(AsyncSpanStore left, AsyncSpanStore right) {
    this.left = left;
    this.right = right;
  }

  @Override public void getTraces(QueryRequest request, Callback<List<List<Span>>> callback) {
    GetTracesDoubleCallback doubleCallback = new GetTracesDoubleCallback(callback);
    left.getTraces(request, doubleCallback);
    right.getTraces(request, doubleCallback);
  }

  static final class GetTracesDoubleCallback extends LenientDoubleCallback<List<List<Span>>> {
    static final Logger LOG = Logger.getLogger(GetTracesDoubleCallback.class.getName());

    GetTracesDoubleCallback(Callback<List<List<Span>>> delegate) {
      super(LOG, delegate);
    }

    // For simplicity, assumes a trace isn't split across storage
    @Override List<List<Span>> merge(List<List<Span>> v1, List<List<Span>> v2) {
      List<List<Span>> result = new ArrayList<>(v1);
      result.addAll(v2);
      return result;
    }
  }

  @Override @Deprecated public void getTrace(long id, Callback<List<Span>> callback) {
    getTrace(0L, id, callback);
  }

  @Override public void getTrace(long traceIdHigh, long traceIdLow, Callback<List<Span>> callback) {
    GetTraceDoubleCallback doubleCallback = new GetTraceDoubleCallback(callback);
    left.getTrace(traceIdHigh, traceIdLow, doubleCallback);
    right.getTrace(traceIdHigh, traceIdLow, doubleCallback);
  }

  static final class GetTraceDoubleCallback extends LenientDoubleCallback<List<Span>> {
    static final Logger LOG = Logger.getLogger(GetTraceDoubleCallback.class.getName());

    GetTraceDoubleCallback(Callback<List<Span>> delegate) {
      super(LOG, delegate);
    }

    @Override List<Span> merge(@Nullable List<Span> v1, @Nullable List<Span> v2) {
      if (v1 == null) return v2;
      if (v2 == null) return v1;
      List<Span> result = new ArrayList<>(v1);
      result.addAll(v2);
      return MergeById.apply(result);
    }
  }

  @Override @Deprecated public void getRawTrace(long traceId, Callback<List<Span>> callback) {
    getRawTrace(0L, traceId, callback);
  }

  @Override
  public void getRawTrace(long traceIdHigh, long traceIdLow, Callback<List<Span>> callback) {
    GetRawTraceDoubleCallback doubleCallback = new GetRawTraceDoubleCallback(callback);
    left.getRawTrace(traceIdHigh, traceIdLow, doubleCallback);
    right.getRawTrace(traceIdHigh, traceIdLow, doubleCallback);
  }

  static final class GetRawTraceDoubleCallback extends LenientDoubleCallback<List<Span>> {
    static final Logger LOG = Logger.getLogger(GetRawTraceDoubleCallback.class.getName());

    GetRawTraceDoubleCallback(Callback<List<Span>> delegate) {
      super(LOG, delegate);
    }

    @Override List<Span> merge(@Nullable List<Span> v1, @Nullable List<Span> v2) {
      if (v1 == null) return v2;
      if (v2 == null) return v1;
      List<Span> result = new ArrayList<>(v1);
      result.addAll(v2);
      return result; // don't merge as this is raw
    }
  }

  @Override public void getServiceNames(Callback<List<String>> callback) {
    StringsDoubleCallback doubleCallback = new StringsDoubleCallback(callback);
    left.getServiceNames(doubleCallback);
    right.getServiceNames(doubleCallback);
  }

  static final class StringsDoubleCallback extends LenientDoubleCallback<List<String>> {
    static final Logger LOG = Logger.getLogger(StringsDoubleCallback.class.getName());

    StringsDoubleCallback(Callback<List<String>> delegate) {
      super(LOG, delegate);
    }

    @Override List<String> merge(List<String> v1, List<String> v2) {
      Set<String> result = new LinkedHashSet<>(v1); // retain order
      result.addAll(v2);
      return new ArrayList<>(result);
    }
  }

  @Override public void getSpanNames(String serviceName, Callback<List<String>> callback) {
    StringsDoubleCallback doubleCallback = new StringsDoubleCallback(callback);
    left.getSpanNames(serviceName, doubleCallback);
    right.getSpanNames(serviceName, doubleCallback);
  }

  @Override
  public void getDependencies(long endTs, @Nullable Long lookback,
    Callback<List<DependencyLink>> callback) {
    GetDependenciesDoubleCallback doubleCallback = new GetDependenciesDoubleCallback(callback);
    left.getDependencies(endTs, lookback, doubleCallback);
    right.getDependencies(endTs, lookback, doubleCallback);
  }

  static final class GetDependenciesDoubleCallback
    extends LenientDoubleCallback<List<DependencyLink>> {
    static final Logger LOG = Logger.getLogger(GetDependenciesDoubleCallback.class.getName());

    GetDependenciesDoubleCallback(Callback<List<DependencyLink>> delegate) {
      super(LOG, delegate);
    }

    @Override List<DependencyLink> merge(List<DependencyLink> v1, List<DependencyLink> v2) {
      List<DependencyLink> concat = new ArrayList<>(v1);
      concat.addAll(v2);
      return DependencyLinker.merge(concat);
    }
  }

  @Override public String toString() {
    return "LenientDoubleCallbackAsyncSpanStore(" + left + "," + right + ")";
  }
}
