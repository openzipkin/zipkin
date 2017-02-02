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
package zipkin.storage.elasticsearch.http;

import com.squareup.moshi.JsonReader;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import okio.BufferedSource;
import zipkin.DependencyLink;
import zipkin.Span;
import zipkin.internal.DependencyLinker;
import zipkin.internal.Util;
import zipkin.storage.elasticsearch.http.internal.client.HttpCall.BodyConverter;
import zipkin.storage.elasticsearch.http.internal.client.SearchResultConverter;

import static zipkin.moshi.JsonReaders.collectValuesNamed;

final class BodyConverters {
  static final BodyConverter<List<String>> SORTED_KEYS = b -> {
    Set<String> result = collectValuesNamed(JsonReader.of(b), "key");
    return Util.sortedList(result);
  };
  static final BodyConverter<List<Span>> SPANS =
      SearchResultConverter.create(JsonAdapters.SPAN_ADAPTER);
  static final BodyConverter<List<Span>> NULLABLE_SPANS =
      SearchResultConverter.create(JsonAdapters.SPAN_ADAPTER).defaultToNull();
  static final BodyConverter<List<DependencyLink>> DEPENDENCY_LINKS =
      new SearchResultConverter<DependencyLink>(JsonAdapters.DEPENDENCY_LINK_ADAPTER) {
        @Override public List<DependencyLink> convert(BufferedSource content) throws IOException {
          List<DependencyLink> result = super.convert(content);
          return result.isEmpty() ? result : DependencyLinker.merge(result);
        }
      };
}
