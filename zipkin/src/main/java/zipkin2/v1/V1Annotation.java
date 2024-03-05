/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.v1;

import java.util.Objects;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.Nullable;

/**
 * Like {@link zipkin2.Annotation}, except in v1 format the {@link Span#localEndpoint()} was
 * repeated for each annotation.
 *
 * @deprecated new code should use {@link Annotation}.
 */
@Deprecated
public final class V1Annotation implements Comparable<V1Annotation> {

  // exposed for conversion
  public static V1Annotation create(long timestamp, String value, @Nullable Endpoint endpoint) {
    return new V1Annotation(timestamp, value, endpoint);
  }

  /** Sets {@link Annotation#timestamp()} */
  public long timestamp() {
    return timestamp;
  }

  /** Sets {@link Annotation#value()} */
  public String value() {
    return value;
  }

  /**
   * The host that reported this annotation or null if unknown.
   *
   * <p>In v2 format, this is analogous to {@link Span#localEndpoint()}.
   */
  @Nullable
  public Endpoint endpoint() {
    return endpoint;
  }

  final long timestamp;
  final String value;
  final Endpoint endpoint;

  V1Annotation(long timestamp, String value, @Nullable Endpoint endpoint) {
    this.timestamp = timestamp;
    if (value == null) throw new NullPointerException("value == null");
    this.value = value;
    this.endpoint = endpoint;
  }

  // hashCode and equals implemented as legacy cassandra uses it in a naming convention
  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (!(o instanceof V1Annotation)) return false;
    V1Annotation that = (V1Annotation) o;
    return timestamp == that.timestamp
        && value.equals(that.value)
        && Objects.equals(endpoint, that.endpoint);
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (int) (h ^ ((timestamp >>> 32) ^ timestamp));
    h *= 1000003;
    h ^= value.hashCode();
    h *= 1000003;
    h ^= endpoint == null ? 0 : endpoint.hashCode();
    return h;
  }

  /** Compares by {@link #timestamp()}, then {@link #value()}. */
  @Override
  public int compareTo(V1Annotation that) {
    if (this == that) return 0;
    int byTimestamp = timestamp < that.timestamp ? -1 : timestamp == that.timestamp ? 0 : 1;
    if (byTimestamp != 0) return byTimestamp;
    return value.compareTo(that.value);
  }
}
