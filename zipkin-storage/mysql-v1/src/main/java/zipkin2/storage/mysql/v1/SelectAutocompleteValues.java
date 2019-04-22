/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
