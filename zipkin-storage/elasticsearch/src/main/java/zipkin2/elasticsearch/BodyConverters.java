/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
