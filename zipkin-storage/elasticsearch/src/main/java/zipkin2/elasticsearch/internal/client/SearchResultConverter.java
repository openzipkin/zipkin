/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.internal.client;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import zipkin2.elasticsearch.internal.JsonSerializers.ObjectParser;

import static zipkin2.elasticsearch.internal.JsonReaders.enterPath;

public class SearchResultConverter<T> implements HttpCall.BodyConverter<List<T>> {
  final ObjectParser<T> adapter;

  public static <T> SearchResultConverter<T> create(ObjectParser<T> adapter) {
    return new SearchResultConverter<>(adapter);
  }

  protected SearchResultConverter(ObjectParser<T> adapter) {
    this.adapter = adapter;
  }

  @Override
  public List<T> convert(JsonParser parser, Supplier<String> contentString) throws IOException {
    JsonParser hits = enterPath(parser, "hits", "hits");
    if (hits == null || !hits.isExpectedStartArrayToken()) return List.of();

    List<T> result = new ArrayList<>();
    while (hits.nextToken() != JsonToken.END_ARRAY) {
      JsonParser source = enterPath(hits, "_source");
      if (source != null) result.add(adapter.parse(source));
    }
    return result.isEmpty() ? List.of() : result;
  }
}
