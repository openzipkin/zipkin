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
package zipkin.storage.elasticsearch.http.internal.client;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

public class SearchCallFactory {
  static final MediaType APPLICATION_JSON = MediaType.parse("application/json");

  final HttpCall.Factory http;
  final JsonAdapter<SearchRequest> searchRequest =
      new Moshi.Builder().build().adapter(SearchRequest.class);

  public SearchCallFactory(HttpCall.Factory http) {
    this.http = http;
  }

  public <V> HttpCall<V> newCall(SearchRequest request, HttpCall.BodyConverter<V> bodyConverter) {
    Request httpRequest = new Request.Builder().url(lenientSearch(request.indices, request.type))
        .post(RequestBody.create(APPLICATION_JSON, searchRequest.toJson(request)))
        .header("Accept-Encoding", "gzip")
        .tag(request.tag()).build();
    return http.newCall(httpRequest, bodyConverter);
  }

  /** Matches the behavior of {@code IndicesOptions#lenientExpandOpen()} */
  public HttpUrl lenientSearch(List<String> indices, String type) {
    return http.baseUrl.newBuilder()
        .addPathSegment(join(indices))
        .addPathSegment(type)
        .addPathSegment("_search")
        // keep these in alphabetical order as it simplifies amazon signatures!
        .addQueryParameter("allow_no_indices", "true")
        .addQueryParameter("expand_wildcards", "open")
        .addQueryParameter("ignore_unavailable", "true").build();
  }

  static String join(List<String> parts) {
    StringBuilder to = new StringBuilder();
    for (int i = 0, length = parts.size(); i < length; i++) {
      to.append(parts.get(i));
      if (i + 1 < length) {
        to.append(',');
      }
    }
    return to.toString();
  }
}
