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
package io.zipkin.dependencies;

import com.facebook.swift.codec.ThriftConstructor;
import com.facebook.swift.codec.ThriftField;
import com.facebook.swift.codec.ThriftStruct;
import com.google.auto.value.AutoValue;

@AutoValue
@ThriftStruct(value = "DependencyLink", builder = AutoValue_DependencyLink.Builder.class)
public abstract class DependencyLink {

  public static Builder builder() {
    return new AutoValue_DependencyLink.Builder();
  }

  public static Builder builder(DependencyLink source) {
    return new AutoValue_DependencyLink.Builder(source);
  }

  /** parent service name (caller) */
  @ThriftField(value = 1)
  public abstract String parent();

  /** child service name (callee) */
  @ThriftField(value = 2)
  public abstract String child();

  /** calls made during the duration (in microseconds) of this link */
  @ThriftField(value = 4)
  public abstract long callCount();

  @AutoValue.Builder
  interface Builder {

    @ThriftField(value = 1)
    Builder parent(String parent);

    @ThriftField(value = 2)
    Builder child(String child);

    @ThriftField(value = 4)
    Builder callCount(long callCount);

    @ThriftConstructor
    DependencyLink build();
  }
}
