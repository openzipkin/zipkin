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
import io.zipkin.internal.ThriftCodec;

/**
 * Methods make an attempt to perform codec operations, failing to null.
 */
public interface Codec {

  Codec JSON = new JsonCodec();
  Codec THRIFT = new ThriftCodec();

  /** Returns null if the span couldn't be decoded */
  @Nullable
  Span readSpan(byte[] bytes);

  /** Returns null if the span couldn't be encoded */
  @Nullable
  byte[] writeSpan(Span value);

  /** Returns null if the dependency link couldn't be decoded */
  @Nullable
  DependencyLink readDependencyLink(byte[] bytes);

  /** Returns null if the dependency link couldn't be encoded */
  @Nullable
  byte[] writeDependencyLink(DependencyLink value);
}
