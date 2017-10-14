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
package zipkin;

import java.util.Arrays;
import zipkin.internal.JsonCodec;
import zipkin.internal.Nullable;
import zipkin.internal.Util;

import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.checkNotNull;
import static zipkin.internal.Util.equal;

/**
 * Binary annotations are tags applied to a Span to give it context. For example, a binary
 * annotation of {@link TraceKeys#HTTP_PATH "http.path"} could the path to a resource in a RPC call.
 *
 * <p>Binary annotations of type {@link Type#STRING} are always queryable, though more a historical
 * implementation detail than a structural concern.
 *
 * <p>Binary annotations can repeat, and vary on the host. Similar to Annotation, the host
 * indicates who logged the event. This allows you to tell the difference between the client and
 * server side of the same key. For example, the key "http.path" might be different on the client and
 * server side due to rewriting, like "/api/v1/myresource" vs "/myresource. Via the host field, you
 * can see the different points of view, which often help in debugging.
 */
public final class BinaryAnnotation implements Comparable<BinaryAnnotation> {

  /** A subset of thrift base types, except BYTES. */
  public enum Type {
    /**
     * Set to 0x01 when {@link BinaryAnnotation#key} is {@link Constants#CLIENT_ADDR} or  {@link
     * Constants#SERVER_ADDR}
     */
    BOOL(0),
    /** No encoding, or type is unknown. */
    BYTES(1),
    I16(2),
    I32(3),
    I64(4),
    DOUBLE(5),
    /** The only type zipkin v1 supports search against. */
    STRING(6);

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

  /**
   * Special-cased form supporting {@link Constants#CLIENT_ADDR} and
   * {@link Constants#SERVER_ADDR}.
   *
   * @param key {@link Constants#CLIENT_ADDR} or {@link Constants#SERVER_ADDR}
   * @param endpoint associated endpoint.
   */
  public static BinaryAnnotation address(String key, Endpoint endpoint) {
    return new BinaryAnnotation(key, new byte[]{1}, Type.BOOL, checkNotNull(endpoint, "endpoint"));
  }

  /** String values are the only queryable type of binary annotation. */
  public static BinaryAnnotation create(String key, String value, @Nullable Endpoint endpoint) {
    checkNotNull(key, "key");
    if (value == null) throw new NullPointerException("value of " + key);
    return new BinaryAnnotation(key, value.getBytes(Util.UTF_8), Type.STRING, endpoint);
  }

  public static BinaryAnnotation create(String key, byte[] value, Type type, @Nullable Endpoint endpoint) {
    return new BinaryAnnotation(key, value, type, endpoint);
  }

  /**
   * Name used to lookup spans, such as {@link TraceKeys#HTTP_PATH "http.path"} or {@link
   * Constants#ERROR "error"}
   */
  public final String key;
  /**
   * Serialized thrift bytes, in TBinaryProtocol format.
   *
   * <p>For legacy reasons, byte order is big-endian. See THRIFT-3217.
   */
  public final byte[] value;
  /**
   * The thrift type of value, most often STRING.
   *
   * <p>Note: type shouldn't vary for the same key.
   */
  public final Type type;

  /**
   * The host that recorded {@link #value}, allowing query by service name or address.
   *
   * <p>There are two exceptions: when {@link #key} is {@link Constants#CLIENT_ADDR} or {@link
   * Constants#SERVER_ADDR}, this is the source or destination of an RPC. This exception allows
   * zipkin to display network context of uninstrumented services, such as browsers or databases.
   */
  @Nullable
  public final Endpoint endpoint;

  BinaryAnnotation(String key, byte[] value, Type type, @Nullable Endpoint endpoint) {
    checkNotNull(key, "key");
    if (value == null) throw new NullPointerException("value of " + key);
    if (type == null) throw new NullPointerException("type of " + key);
    this.key = key;
    this.value = value;
    this.type = type;
    this.endpoint = endpoint;
  }

  public Builder toBuilder(){
    return new Builder(this);
  }

  public static Builder builder(){
    return new Builder();
  }

  public static final class Builder {
    private String key;
    private byte[] value;
    private Type type;
    private Endpoint endpoint;

    Builder() {
    }

    Builder(BinaryAnnotation source) {
      this.key = source.key;
      this.value = source.value;
      this.type = source.type;
      this.endpoint = source.endpoint;
    }

    /** @see BinaryAnnotation#key */
    public BinaryAnnotation.Builder key(String key) {
      this.key = key;
      return this;
    }

    /** @see BinaryAnnotation#value */
    public BinaryAnnotation.Builder value(byte[] value) {
      this.value = value.clone();
      return this;
    }

    /** @see BinaryAnnotation#value */
    public BinaryAnnotation.Builder value(String value) {
      this.value = value.getBytes(Util.UTF_8);
      this.type = Type.STRING;
      return this;
    }

    /** @see BinaryAnnotation#type */
    public Builder type(Type type) {
      this.type = type;
      return this;
    }

    /** @see BinaryAnnotation#endpoint */
    public BinaryAnnotation.Builder endpoint(@Nullable Endpoint endpoint) {
      this.endpoint = endpoint;
      return this;
    }

    public BinaryAnnotation build() {
      return new BinaryAnnotation(key, value, type, endpoint);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
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

  /** Provides consistent iteration by {@link #key} */
  @Override
  public int compareTo(BinaryAnnotation that) {
    if (this == that) return 0;
    return key.compareTo(that.key);
  }

  @Override public String toString() {
    return new String(JsonCodec.writeBinaryAnnotation(this), UTF_8);
  }
}
