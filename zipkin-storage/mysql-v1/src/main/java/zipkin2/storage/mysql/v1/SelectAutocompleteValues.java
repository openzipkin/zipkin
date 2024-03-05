/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import java.util.List;
import java.util.function.Function;
import org.jooq.Converter;
import org.jooq.DSLContext;
import zipkin2.v1.V1BinaryAnnotation;

import static java.nio.charset.StandardCharsets.UTF_8;
import static zipkin2.storage.mysql.v1.internal.generated.tables.ZipkinAnnotations.ZIPKIN_ANNOTATIONS;

final class SelectAutocompleteValues implements Function<DSLContext, List<String>> {
  final Schema schema;
  final String autocompleteKey;

  SelectAutocompleteValues(Schema schema, String autocompleteKey) {
    this.schema = schema;
    this.autocompleteKey = autocompleteKey;
  }

  @Override public List<String> apply(DSLContext context) {
    return context.selectDistinct(ZIPKIN_ANNOTATIONS.A_VALUE)
      .from(ZIPKIN_ANNOTATIONS)
      .where(ZIPKIN_ANNOTATIONS.A_TYPE.eq(V1BinaryAnnotation.TYPE_STRING)
        .and(ZIPKIN_ANNOTATIONS.A_KEY.eq(autocompleteKey)))
      .fetch(ZIPKIN_ANNOTATIONS.A_VALUE, STRING_CONVERTER);
  }

  static final Converter<byte[], String> STRING_CONVERTER = new Converter<byte[], String>() {
    @Override public String from(byte[] bytes) {
      return new String(bytes, UTF_8);
    }

    @Override public byte[] to(String input) {
      return input.getBytes(UTF_8);
    }

    @Override public Class<byte[]> fromType() {
      return byte[].class;
    }

    @Override public Class<String> toType() {
      return String.class;
    }
  };
}
