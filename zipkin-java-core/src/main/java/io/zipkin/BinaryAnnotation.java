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

import io.zipkin.internal.JsonCodec;
import io.zipkin.internal.Nullable;
import java.util.Arrays;

import static io.zipkin.internal.Util.checkNotNull;
import static io.zipkin.internal.Util.equal;

public final class BinaryAnnotation {

  public enum Type {
    BOOL(0), BYTES(1), I16(2), I32(3), I64(4), DOUBLE(5), STRING(6);

    public final int value;

    Type(int value) {
      this.value = value;
    }

    /** Returns {@link Type#BYTES} if unknown. */
    public static Type fromValue(int value) {
      switch (value) {
        case 0:
          return BOOL;
        case 1:
          return BYTES;
        case 2:
          return I16;
        case 3:
          return I32;
        case 4:
          return I64;
        case 5:
          return DOUBLE;
        case 6:
          return STRING;
        default:
          return BYTES;
      }
    }
  }

  public static BinaryAnnotation create(String key, byte[] value, Type type, @Nullable Endpoint endpoint) {
    return new BinaryAnnotation(key, value, type, endpoint);
  }

  public final String key;

  public final byte[] value;

  public final Type type;

  /** The endpoint that recorded this annotation */
  @Nullable
  public final Endpoint endpoint;

  BinaryAnnotation(String key, byte[] value, Type type, Endpoint endpoint) {
    this.key = checkNotNull(key, "key");
    this.value = checkNotNull(value, "value");
    this.type = checkNotNull(type, "type");
    this.endpoint = endpoint;
  }

  public static final class Builder {
    private String key;
    private byte[] value;
    private Type type;
    private Endpoint endpoint;

    public Builder() {
    }

    public Builder(BinaryAnnotation source) {
      this.key = source.key;
      this.value = source.value;
      this.type = source.type;
      this.endpoint = source.endpoint;
    }

    public BinaryAnnotation.Builder key(String key) {
      this.key = key;
      return this;
    }

    public BinaryAnnotation.Builder value(byte[] value) {
      this.value = value.clone();
      return this;
    }

    public BinaryAnnotation.Builder type(Type type) {
      this.type = type;
      return this;
    }

    @Nullable
    public BinaryAnnotation.Builder endpoint(Endpoint endpoint) {
      this.endpoint = endpoint;
      return this;
    }

    public BinaryAnnotation build() {
      return new BinaryAnnotation(key, value, type, endpoint);
    }
  }

  @Override
  public String toString() {
    return JsonCodec.BINARY_ANNOTATION_ADAPTER.toJson(this);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof BinaryAnnotation) {
      BinaryAnnotation that = (BinaryAnnotation) o;
      return (this.key.equals(that.key))
          && (Arrays.equals(this.value, that.value))
          && (this.type.equals(that.type))
          && equal(this.endpoint, that.endpoint);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= key.hashCode();
    h *= 1000003;
    h ^= Arrays.hashCode(value);
    h *= 1000003;
    h ^= type.hashCode();
    h *= 1000003;
    h ^= (endpoint == null) ? 0 : endpoint.hashCode();
    return h;
  }
}
