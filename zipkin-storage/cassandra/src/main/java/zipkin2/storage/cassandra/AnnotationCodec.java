/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.core.type.codec.MappingCodec;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.datastax.oss.driver.api.core.type.reflect.GenericType;
import zipkin2.Annotation;
import zipkin2.internal.Nullable;

/**
 * For better performance, this relies on ordinals instead of name lookups.
 *
 * <p>0 = "ts" = {@link Annotation#timestamp()}
 * <p>0 = "v" = {@link Annotation#value()}
 */
final class AnnotationCodec extends MappingCodec<UdtValue, Annotation> {
  AnnotationCodec(TypeCodec<UdtValue> innerCodec) {
    super(innerCodec, GenericType.of(Annotation.class));
  }

  @Override public UserDefinedType getCqlType() {
    return (UserDefinedType) super.getCqlType();
  }

  @Nullable @Override protected Annotation innerToOuter(@Nullable UdtValue value) {
    if (value == null || value.isNull(0) || value.isNull(1)) return null;
    return Annotation.create(value.getLong(0), value.getString(1));
  }

  @Nullable @Override protected UdtValue outerToInner(@Nullable Annotation value) {
    if (value == null) return null;
    return getCqlType().newValue().setLong(0, value.timestamp()).setString(1, value.value());
  }
}
