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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.facebook.swift.codec.ThriftField.Requiredness.OPTIONAL;

@AutoValue
@ThriftStruct(value = "Span", builder = AutoValue_Span.Builder.class)
public abstract class Span {

  public static Builder builder() {
    return new AutoValue_Span.Builder();
  }

  public static Builder builder(Span source) {
    return new AutoValue_Span.Builder(source);
  }

  @ThriftField(value = 1)
  public abstract long traceId();

  @ThriftField(value = 3)
  public abstract String name();

  @ThriftField(value = 4)
  public abstract long id();

  @Nullable
  @ThriftField(value = 5, requiredness = OPTIONAL)
  public abstract Long parentId();

  @ThriftField(value = 6)
  public abstract List<Annotation> annotations();

  @ThriftField(value = 8)
  public abstract List<BinaryAnnotation> binaryAnnotations();

  @Nullable
  @ThriftField(value = 9, requiredness = OPTIONAL)
  public abstract Boolean debug();

  /**
   * Assuming this is an RPC span, is it from the client side?
   */
  public boolean isClientSide() {
    for (Annotation a : annotations()) {
      if (a.value().equals(Constants.CLIENT_SEND) || a.value().equals(Constants.CLIENT_RECV)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the lower-cased set of service names this span has annotations for.
   */
  public Set<String> serviceNames() {
    Set<String> result = new LinkedHashSet<>();
    for (Annotation a : annotations()) {
      if (a.host() != null) {
        result.add(a.host().serviceName().toLowerCase());
      }
    }
    return result;
  }

  @AutoValue.Builder
  public interface Builder {

    @ThriftField(value = 1)
    Builder traceId(long traceId);

    @ThriftField(value = 3)
    Builder name(String name);

    @ThriftField(value = 4)
    Builder id(long id);

    @Nullable
    @ThriftField(value = 5, requiredness = OPTIONAL)
    Builder parentId(Long parentId);

    @ThriftField(value = 6)
    Builder annotations(List<Annotation> annotations);

    @ThriftField(value = 8)
    Builder binaryAnnotations(List<BinaryAnnotation> binaryAnnotations);

    @Nullable
    @ThriftField(value = 9, requiredness = OPTIONAL)
    Builder debug(Boolean debug);

    @ThriftConstructor
    Span build();
  }
}
