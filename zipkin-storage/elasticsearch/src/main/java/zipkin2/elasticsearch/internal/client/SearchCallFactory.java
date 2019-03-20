/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.elasticsearch.internal.client;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import zipkin2.internal.Nullable;

import java.util.List;

public class SearchCallFactory {
  final RestClient client;
  final JsonAdapter<SearchRequest> searchRequest =
      new Moshi.Builder().build().adapter(SearchRequest.class);

  public SearchCallFactory(RestClient client) {
    this.client = client;
  }

  public <V> RestCall<V> newCall(SearchRequest request, ResponseConverter<V> responseConverter) {
    Request httpRequest = lenientSearch(request.indices, request.type)
        .jsonEntity(searchRequest.toJson(request))
        .tag(request.tag()).build();
    return new RestCall<>(client, httpRequest, responseConverter);
  }

  /** Matches the behavior of {@code IndicesOptions#lenientExpandOpen()} */
  RequestBuilder lenientSearch(List<String> indices, @Nullable String type) {
    RequestBuilder builder = type == null ?
      RequestBuilder.post(join(indices), "_search") :
      RequestBuilder.post(join(indices), type, "_search") ;
    // keep these in alphabetical order as it simplifies amazon signatures!
    // TODO Elasticsearch Client uses an HashMap and doesn't guarantee parameter order
    return builder.parameter("allow_no_indices", "true")
                  .parameter("expand_wildcards", "open")
                  .parameter("ignore_unavailable", "true");
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
