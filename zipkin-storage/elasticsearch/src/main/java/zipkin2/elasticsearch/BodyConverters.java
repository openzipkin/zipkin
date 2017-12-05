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
package zipkin2.elasticsearch;

import com.squareup.moshi.JsonReader;
import java.io.IOException;
import java.util.List;
import okio.BufferedSource;
import zipkin2.DependencyLink;
import zipkin2.Span;
import zipkin2.elasticsearch.internal.client.HttpCall;
import zipkin2.elasticsearch.internal.client.SearchResultConverter;
import zipkin2.internal.DependencyLinker;

import static zipkin2.elasticsearch.internal.JsonReaders.collectValuesNamed;

public final class BodyConverters {
  static final HttpCall.BodyConverter<Object> NULL = new HttpCall.BodyConverter<Object>() {
    @Override public Object convert(BufferedSource content) throws IOException {
      return null;
    }
  };
  static final HttpCall.BodyConverter<List<String>> KEYS =
    new HttpCall.BodyConverter<List<String>>() {
      @Override public List<String> convert(BufferedSource b) throws IOException {
        return collectValuesNamed(JsonReader.of(b), "key");
      }
    };
  static final HttpCall.BodyConverter<List<Span>> SPANS =
    SearchResultConverter.create(JsonAdapters.SPAN_ADAPTER);
  static final HttpCall.BodyConverter<List<DependencyLink>> DEPENDENCY_LINKS =
    new SearchResultConverter<DependencyLink>(JsonAdapters.DEPENDENCY_LINK_ADAPTER) {
      @Override public List<DependencyLink> convert(BufferedSource content) throws IOException {
        List<DependencyLink> result = super.convert(content);
        return result.isEmpty() ? result : DependencyLinker.merge(result);
      }
    };
}
