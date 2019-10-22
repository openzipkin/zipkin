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
package zipkin2.elasticsearch;

import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.elasticsearch.internal.JsonSerializers;
import zipkin2.elasticsearch.internal.client.HttpCall.BodyConverter;
import zipkin2.elasticsearch.internal.client.SearchResultConverter;
import zipkin2.internal.DependencyLinker;

import static zipkin2.elasticsearch.internal.JsonReaders.collectValuesNamed;

final class BodyConverters {
  static final BodyConverter<Object> NULL = (parser, contentString) -> null;
  static final BodyConverter<List<String>> KEYS =
    (parser, contentString) -> collectValuesNamed(parser, "key");
  static final BodyConverter<List<Span>> SPANS =
    SearchResultConverter.create(JsonSerializers.SPAN_PARSER);
  static final BodyConverter<List<DependencyLink>> DEPENDENCY_LINKS =
    new SearchResultConverter<DependencyLink>(JsonSerializers.DEPENDENCY_LINK_PARSER) {
      @Override
      public List<DependencyLink> convert(JsonParser parser, Supplier<String> contentString)
        throws IOException {
        List<DependencyLink> result = super.convert(parser, contentString);
        return result.isEmpty() ? result : DependencyLinker.merge(result);
      }
    };
}
