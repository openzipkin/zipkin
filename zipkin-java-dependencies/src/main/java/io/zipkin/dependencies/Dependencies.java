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
import java.util.List;

@AutoValue
@ThriftStruct(value = "Dependencies", builder = AutoValue_Dependencies.Builder.class)
public abstract class Dependencies {

  public static Builder builder() {
    return new AutoValue_Dependencies.Builder();
  }

  public static Builder builder(Dependencies source) {
    return new AutoValue_Dependencies.Builder(source);
  }

  /** microseconds from epoch */
  @ThriftField(value = 1)
  public abstract long startTime();

  /** microseconds from epoch */
  @ThriftField(value = 2)
  public abstract long endTime();

  @ThriftField(value = 3)
  public abstract List<DependencyLink> links();

  @AutoValue.Builder
  interface Builder {

    @ThriftField(value = 1)
    Builder startTime(long startTime);

    @ThriftField(value = 2)
    Builder endTime(long endTime);

    @ThriftField(value = 3)
    Builder links(List<DependencyLink> links);

    @ThriftConstructor
    Dependencies build();
  }
}
