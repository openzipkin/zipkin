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
package io.zipkin.query;

import com.facebook.swift.codec.ThriftConstructor;
import com.facebook.swift.codec.ThriftField;
import com.facebook.swift.codec.ThriftStruct;
import com.google.auto.value.AutoValue;
import io.zipkin.BinaryAnnotation;
import io.zipkin.internal.Nullable;
import java.util.List;
import java.util.Map;

import static com.facebook.swift.codec.ThriftField.Requiredness.OPTIONAL;

@AutoValue
@ThriftStruct(value = "QueryRequest", builder = AutoValue_QueryRequest.Builder.class)
public abstract class QueryRequest {

  public static Builder builder() {
    return new AutoValue_QueryRequest.Builder().adjustClockSkew(true);
  }

  @ThriftField(value = 1)
  public abstract String serviceName();

  @Nullable
  @ThriftField(value = 2, requiredness = OPTIONAL)
  public abstract String spanName();

  /**
   * Custom-defined annotation values to search for.
   */
  @Nullable
  @ThriftField(value = 3, requiredness = OPTIONAL)
  public abstract List<String> annotations();

  /**
   * Binary annotations of type {@link BinaryAnnotation.Type#STRING} to search for.
   */
  @Nullable
  @ThriftField(value = 8, requiredness = OPTIONAL)
  public abstract Map<String, String> binaryAnnotations();

  /** results will have epoch microsecond timestamps before this value */
  @ThriftField(value = 5)
  public abstract long endTs();

  /** maximum entries to return before "end_ts" */
  @ThriftField(value = 6)
  public abstract int limit();

  @ThriftField(value = 9)
  public abstract boolean adjustClockSkew();

  @AutoValue.Builder
  public interface Builder {

    @ThriftField(value = 1)
    Builder serviceName(String serviceName);

    @ThriftField(value = 2, requiredness = OPTIONAL)
    Builder spanName(String spanName);

    @ThriftField(value = 3, requiredness = OPTIONAL)
    Builder annotations(List<String> annotations);

    @ThriftField(value = 8, requiredness = OPTIONAL)
    Builder binaryAnnotations(Map<String, String> binaryAnnotations);

    @ThriftField(value = 5)
    Builder endTs(long endTs);

    @ThriftField(value = 6)
    Builder limit(int limit);

    @ThriftField(value = 9)
    Builder adjustClockSkew(boolean adjustClockSkew);

    @ThriftConstructor
    QueryRequest build();
  }
}