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
import com.facebook.swift.codec.ThriftField;
import com.facebook.swift.codec.ThriftStruct;
import com.google.auto.value.AutoValue;
import io.zipkin.internal.Nullable;

import static com.facebook.swift.codec.ThriftField.Requiredness.OPTIONAL;

@AutoValue
@ThriftStruct(value = "Annotation", builder = AutoValue_Annotation.Builder.class)
public abstract class Annotation implements Comparable<Annotation> {

  @Override
  public int compareTo(Annotation that) {
    if (this == that) {
      return 0;
    }
    return Long.compare(timestamp(), that.timestamp());
  }

  public static Builder builder() {
    return new AutoValue_Annotation.Builder();
  }

  public static Builder builder(Annotation source) {
    return new AutoValue_Annotation.Builder(source);
  }

  /** Microseconds from epoch */
  @ThriftField(value = 1)
  public abstract long timestamp();

  /** What happened at the timestamp? */
  @ThriftField(value = 2)
  public abstract String value();

  /**
   * The endpoint associated with this annotation depends on {@link #value()}.
   *
   * <p/>When {@link #value()} is... <ul> <li>{@link Constants#CLIENT_ADDR}, this is the client
   * endpoint of an RPC call</li> <li>{@link Constants#SERVER_ADDR}, this is the server endpoint of
   * an RPC call</li> <li>Otherwise, this is the endpoint that recorded this annotation</li> </ul
   */
  @Nullable
  @ThriftField(value = 3, requiredness = OPTIONAL)
  public abstract Endpoint endpoint();

  @AutoValue.Builder
  public interface Builder {

    @ThriftField(value = 1)
    Builder timestamp(long timestamp);

    @ThriftField(value = 2)
    Builder value(String value);

    @ThriftField(value = 3, requiredness = OPTIONAL)
    Builder endpoint(Endpoint endpoint);

    @ThriftConstructor
    Annotation build();
  }
}
