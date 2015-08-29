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
import java.util.List;

@AutoValue
@ThriftStruct(value = "Trace", builder = AutoValue_Trace.Builder.class)
public abstract class Trace {

  public static Trace create(List<Span> spans) {
    return new AutoValue_Trace.Builder().spans(spans).build();
  }

  @ThriftField(value = 1)
  public abstract List<Span> spans();

  @AutoValue.Builder
  interface Builder { // package private as only used for deserialization
    @ThriftField(value = 1)
    Builder spans(List<Span> spans);

    @ThriftConstructor
    Trace build();
  }
}
