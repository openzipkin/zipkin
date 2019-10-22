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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
    if (hits == null || !hits.isExpectedStartArrayToken()) return Collections.emptyList();

    List<T> result = new ArrayList<>();
    while (hits.nextToken() != JsonToken.END_ARRAY) {
      JsonParser source = enterPath(hits, "_source");
      if (source != null) result.add(adapter.parse(source));
    }
    return result.isEmpty() ? Collections.emptyList() : result;
  }
}
