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
package zipkin.junit.v2;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import zipkin.internal.Nullable;
import zipkin2.Call;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.codec.DependencyLinkBytesDecoder;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.internal.JsonCodec;
import zipkin2.internal.JsonCodec.JsonReader;
import zipkin2.internal.V2SpanReader;
import zipkin2.storage.QueryRequest;
import zipkin2.storage.SpanStore;

/** Implements the span store interface by forwarding requests over http. */
final class HttpV2SpanStore implements SpanStore {
  static final JsonAdapter<List<String>> STRING_LIST_ADAPTER =
    new Moshi.Builder().build().adapter(Types.newParameterizedType(List.class, String.class));

  final HttpV2Call.Factory factory;

  HttpV2SpanStore(OkHttpClient client, HttpUrl baseUrl) {
    this.factory = new HttpV2Call.Factory(client, baseUrl);
  }

  @Override public Call<List<List<Span>>> getTraces(QueryRequest request) {
    HttpUrl.Builder url = factory.baseUrl.newBuilder("/api/v2/traces");
    maybeAddQueryParam(url, "serviceName", request.serviceName());
    maybeAddQueryParam(url, "spanName", request.spanName());
    maybeAddQueryParam(url, "annotationQuery", request.annotationQueryString());
    maybeAddQueryParam(url, "minDuration", request.minDuration());
    maybeAddQueryParam(url, "maxDuration", request.maxDuration());
    maybeAddQueryParam(url, "endTs", request.endTs());
    maybeAddQueryParam(url, "lookback", request.lookback());
    maybeAddQueryParam(url, "limit", request.limit());
    return factory.newCall(new Request.Builder().url(url.build()).build(),
      content -> JsonCodec.readList(new SpanListReader(), content.readByteArray()));
  }

  @Override public Call<List<Span>> getTrace(String traceId) {
    return factory.newCall(new Request.Builder()
      .url(factory.baseUrl.resolve("/api/v2/trace/" + Span.normalizeTraceId(traceId)))
      .build(), content -> SpanBytesDecoder.JSON_V2.decodeList(content.readByteArray()))
      .handleError(((error, callback) -> {
        if (error instanceof HttpException && ((HttpException) error).code == 404) {
          callback.onSuccess(Collections.emptyList());
        } else {
          callback.onError(error);
        }
      }));
  }

  @Override
  public Call<List<String>> getServiceNames() {
    return factory.newCall(new Request.Builder()
      .url(factory.baseUrl.resolve("/api/v2/services"))
      .build(), STRING_LIST_ADAPTER::fromJson);
  }

  @Override
  public Call<List<String>> getSpanNames(String serviceName) {
    return factory.newCall(new Request.Builder()
      .url(factory.baseUrl.resolve("/api/v2/spans?serviceName=" + serviceName))
      .build(), STRING_LIST_ADAPTER::fromJson);
  }

  @Override public Call<List<DependencyLink>> getDependencies(long endTs, long lookback) {
    return factory.newCall(new Request.Builder()
      .url(factory.baseUrl.resolve("/api/v2/dependencies?endTs=" + endTs + "&lookback=" + lookback))
      .build(), content -> DependencyLinkBytesDecoder.JSON_V1.decodeList(content.readByteArray()));
  }

  static final class SpanListReader implements JsonCodec.JsonReaderAdapter<List<Span>> {
    V2SpanReader spanReader;

    @Override public List<Span> fromJson(JsonReader reader) throws IOException {
      reader.beginArray();
      if (!reader.hasNext()) {
        reader.endArray();
        return Collections.emptyList();
      }
      List<Span> result = new LinkedList<>(); // because we don't know how long it will be
      if (spanReader == null) spanReader = new V2SpanReader();
      while (reader.hasNext()) result.add(spanReader.fromJson(reader));
      reader.endArray();
      return result;
    }

    @Override public String toString() {
      return "List<Span>";
    }
  }

  void maybeAddQueryParam(HttpUrl.Builder builder, String name, @Nullable Object value) {
    if (value != null) builder.addQueryParameter(name, value.toString());
  }
}
