/**
 * Copyright 2015 The OpenZipkin Authors
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
package io.zipkin;

import com.facebook.swift.codec.ThriftConstructor;
import com.facebook.swift.codec.ThriftEnumValue;
import com.facebook.swift.codec.ThriftField;
import com.facebook.swift.codec.ThriftStruct;
import com.google.auto.value.AutoValue;
import io.zipkin.internal.Nullable;

import static com.facebook.swift.codec.ThriftField.Requiredness.OPTIONAL;

@AutoValue
@ThriftStruct(value = "BinaryAnnotation", builder = AutoValue_BinaryAnnotation.Builder.class)
public abstract class BinaryAnnotation {

  public enum Type {
    BOOL(0), BYTES(1), I16(2), I32(3), I64(4), DOUBLE(5), STRING(6);

    private final int value;

    Type(int value) {
      this.value = value;
    }

    @ThriftEnumValue
    public int value() {
      return value;
    }
  }

  public static Builder builder() {
    return new AutoValue_BinaryAnnotation.Builder();
  }

  public static Builder builder(BinaryAnnotation source) {
    return new AutoValue_BinaryAnnotation.Builder(source);
  }

  @ThriftField(value = 1)
  public abstract String key();

  @ThriftField(value = 2)
  public abstract byte[] value();

  @ThriftField(value = 3)
  public abstract Type type();

  @Nullable
  @ThriftField(value = 4, requiredness = OPTIONAL)
  public abstract Endpoint host();

  @AutoValue.Builder
  public interface Builder {

    @ThriftField(value = 1)
    Builder key(String key);

    @ThriftField(value = 2)
    Builder value(byte[] value);

    @ThriftField(value = 3)
    Builder type(Type type);

    @Nullable
    @ThriftField(value = 4, requiredness = OPTIONAL)
    Builder host(Endpoint host);

    @ThriftConstructor
    BinaryAnnotation build();
  }
}
