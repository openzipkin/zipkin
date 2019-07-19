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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.elasticsearch.internal.JsonSerializers;
import zipkin2.elasticsearch.internal.client.HttpCall;
import zipkin2.elasticsearch.internal.client.SearchResultConverter;
import zipkin2.internal.DependencyLinker;

import static zipkin2.elasticsearch.internal.JsonReaders.collectValuesNamed;

final class BodyConverters {

  static final HttpCall.BodyConverter<Object> NULL =
      new HttpCall.BodyConverter<Object>() {
        @Override
        public Object convert(ByteBuffer content) {
          return null;
        }
      };
  static final HttpCall.BodyConverter<List<String>> KEYS =
      new HttpCall.BodyConverter<List<String>>() {
        @Override
        public List<String> convert(ByteBuffer content) throws IOException {
          return collectValuesNamed(JsonSerializers.jsonParser(content), "key");
        }
      };
  static final HttpCall.BodyConverter<List<Span>> SPANS =
      SearchResultConverter.create(JsonSerializers.SPAN_PARSER);
  static final HttpCall.BodyConverter<List<DependencyLink>> DEPENDENCY_LINKS =
      new SearchResultConverter<DependencyLink>(JsonSerializers.DEPENDENCY_LINK_PARSER) {
        @Override
        public List<DependencyLink> convert(ByteBuffer content) throws IOException {
          List<DependencyLink> result = super.convert(content);
          return result.isEmpty() ? result : DependencyLinker.merge(result);
        }
      };
}
