/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.internal.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestHeaders;
import java.util.List;
import zipkin2.internal.Nullable;

import static zipkin2.elasticsearch.internal.JsonSerializers.OBJECT_MAPPER;

public class SearchCallFactory {
  final HttpCall.Factory http;

  public SearchCallFactory(HttpCall.Factory http) {
    this.http = http;
  }

  public <V> HttpCall<V> newCall(SearchRequest request, HttpCall.BodyConverter<V> bodyConverter) {
    final AggregatedHttpRequest httpRequest;
    try {
      httpRequest = AggregatedHttpRequest.of(
        RequestHeaders.of(
          HttpMethod.POST, lenientSearch(request.indices, request.type),
          HttpHeaderNames.CONTENT_TYPE, MediaType.JSON_UTF_8),
        HttpData.wrap(OBJECT_MAPPER.writeValueAsBytes(request)));
    } catch (JsonProcessingException e) {
      throw new AssertionError("Could not serialize SearchRequest to bytes.", e);
    }
    return http.newCall(httpRequest, bodyConverter, request.tag());
  }

  /** Matches the behavior of {@code IndicesOptions#lenientExpandOpen()} */
  String lenientSearch(List<String> indices, @Nullable String type) {
    return '/' + String.join(",", indices) +
      "/_search?allow_no_indices=true&expand_wildcards=open&ignore_unavailable=true";
  }
}
