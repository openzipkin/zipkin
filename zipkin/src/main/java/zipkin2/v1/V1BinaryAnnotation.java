/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.v1;

import java.util.Objects;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.Nullable;

/**
 * This only supports binary annotations that map to {@link Span v2 span} data. Namely, this
 * supports {@link Span#tags()}, {@link Span#localEndpoint()} and {@link Span#remoteEndpoint()}.
 *
 * <p>Specifically, this maps String and Boolean binary annotations, ignoring others.
 *
 * @deprecated new code should use {@link Span#tags()}.
 */
@Deprecated
public final class V1BinaryAnnotation implements Comparable<V1BinaryAnnotation> {
  /** The defined in zipkin's thrift definition */
  public static final int TYPE_BOOLEAN = 0;
  /** The type defined in zipkin's thrift definition */
  public static final int TYPE_STRING = 6;

  /** Creates an address annotation, which is the same as {@link Span#remoteEndpoint()} */
  public static V1BinaryAnnotation createAddress(String address, Endpoint endpoint) {
    if (endpoint == null) throw new NullPointerException("endpoint == null");
    return new V1BinaryAnnotation(address, null, endpoint);
  }

  /**
   * Creates a tag annotation, which is the same as {@link Span#tags()} except duplicating the
   * endpoint.
   *
   * <p>A special case is when the key is "lc" and value is empty: This substitutes for the {@link
   * Span#localEndpoint()}.
   */
  public static V1BinaryAnnotation createString(String key, String value, Endpoint endpoint) {
    if (value == null) throw new NullPointerException("value == null");
    return new V1BinaryAnnotation(key, value, endpoint);
  }

  /** The same as the key of a {@link Span#tags()} v2 span tag} */
  public String key() {
    return key;
  }

  /**
   * The thrift type for the value defined in Zipkin's thrift definition. Note this is not the
   * TBinaryProtocol field type!
   */
  public int type() {
    return type;
  }

  /** The same as the value of a {@link Span#tags()} v2 span tag} or null if this is an address */
  @Nullable
  public String stringValue() {
    return stringValue;
  }

  /**
   * When {@link #stringValue()} is present, this is the same as the {@link Span#localEndpoint()}
   * Otherwise, it is the same as the {@link Span#remoteEndpoint()}.
   */
  public Endpoint endpoint() {
    return endpoint;
  }

  final String key, stringValue;
  final int type;
  final Endpoint endpoint;

  V1BinaryAnnotation(String key, String stringValue, Endpoint endpoint) {
    if (key == null) throw new NullPointerException("key == null");
    this.key = key;
    this.stringValue = stringValue;
    this.type = stringValue != null ? TYPE_STRING : TYPE_BOOLEAN;
    this.endpoint = endpoint;
  }

  // hashCode and equals implemented as legacy cassandra uses it in a naming convention
  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof V1BinaryAnnotation)) return false;
    V1BinaryAnnotation that = (V1BinaryAnnotation) o;
    return key.equals(that.key)
        && Objects.equals(stringValue, that.stringValue)
        && Objects.equals(endpoint, that.endpoint);
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= key.hashCode();
    h *= 1000003;
    h ^= stringValue == null ? 0 : stringValue.hashCode();
    h *= 1000003;
    h ^= endpoint == null ? 0 : endpoint.hashCode();
    return h;
  }

  /** Provides consistent iteration by {@link #key} */
  @Override
  public int compareTo(V1BinaryAnnotation that) {
    if (this == that) return 0;
    return key.compareTo(that.key);
  }
}
