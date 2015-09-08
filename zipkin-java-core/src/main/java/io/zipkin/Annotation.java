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
public abstract class Annotation {

  public static Builder builder() {
    return new AutoValue_Annotation.Builder();
  }

  public static Builder builder(Annotation source) {
    return new AutoValue_Annotation.Builder(source);
  }

  @ThriftField(value = 1)
  public abstract long timestamp();

  @ThriftField(value = 2)
  public abstract String value();

  @Nullable
  @ThriftField(value = 3, requiredness = OPTIONAL)
  public abstract Endpoint host();

  @Nullable
  @ThriftField(value = 4, requiredness = OPTIONAL)
  public abstract Integer duration();

  @AutoValue.Builder
  public interface Builder {

    @ThriftField(value = 1)
    Builder timestamp(long timestamp);

    @ThriftField(value = 2)
    Builder value(String value);

    @ThriftField(value = 3, requiredness = OPTIONAL)
    Builder host(Endpoint host);

    @ThriftField(value = 4, requiredness = OPTIONAL)
    Builder duration(Integer duration);

    @ThriftConstructor
    Annotation build();
  }
}
