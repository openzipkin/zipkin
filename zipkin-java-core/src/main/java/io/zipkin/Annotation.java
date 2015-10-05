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

import static io.zipkin.internal.Util.checkNotNull;
import static io.zipkin.internal.Util.equal;

/**
 * The endpoint associated with this annotation depends on {@link #value}.
 *
 * <p/>When {@link #value} is...
 * <ul>
 *   <li>{@link Constants#CLIENT_ADDR}, this is the client endpoint of an RPC call</li>
 *   <li>{@link Constants#SERVER_ADDR}, this is the server endpoint of an RPC call</li>
 *   <li>Otherwise, this is the endpoint that recorded this annotation</li>
 * </ul>
 */
public final class Annotation implements Comparable<Annotation> {

  public static Annotation create(long timestamp, String value, @Nullable Endpoint endpoint) {
    return new Annotation(timestamp, value, endpoint);
  }

  /** Microseconds from epoch */
  public final long timestamp;

  /** What happened at the timestamp? */
  public final String value;

  @Nullable
  public final Endpoint endpoint;

  Annotation(long timestamp, String value, Endpoint endpoint) {
    this.timestamp = timestamp;
    this.value = checkNotNull(value, "value");
    this.endpoint = endpoint;
  }

  public static final class Builder {
    private Long timestamp;
    private String value;
    private Endpoint endpoint;

    public Builder() {
    }

    public Builder(Annotation source) {
      this.timestamp = source.timestamp;
      this.value = source.value;
      this.endpoint = source.endpoint;
    }

    public Annotation.Builder timestamp(long timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Annotation.Builder value(String value) {
      this.value = value;
      return this;
    }

    public Annotation.Builder endpoint(Endpoint endpoint) {
      this.endpoint = endpoint;
      return this;
    }

    public Annotation build() {
      return Annotation.create(this.timestamp, this.value, this.endpoint);
    }
  }

  @Override
  public String toString() {
    return JsonCodec.ANNOTATION_ADAPTER.toJson(this);
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Annotation) {
      Annotation that = (Annotation) o;
      return (this.timestamp == that.timestamp)
          && (this.value.equals(that.value))
          && equal(this.endpoint, that.endpoint);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= (timestamp >>> 32) ^ timestamp;
    h *= 1000003;
    h ^= value.hashCode();
    h *= 1000003;
    h ^= (endpoint == null) ? 0 : endpoint.hashCode();
    return h;
  }

  @Override
  public int compareTo(Annotation that) {
    if (this == that) {
      return 0;
    }
    return Long.compare(timestamp, that.timestamp);
  }
}
