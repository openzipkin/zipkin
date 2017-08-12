/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin;

import java.util.List;
import zipkin.internal.JsonCodec;
import zipkin.internal.ThriftCodec;

/**
 * Methods make an attempt to perform codec operations, failing to null.
 */
public interface Codec extends SpanDecoder {

  JsonCodec JSON = new JsonCodec();
  ThriftCodec THRIFT = new ThriftCodec();

  int sizeInBytes(Span value);

  byte[] writeSpan(Span value);

  byte[] writeSpans(List<Span> value);

  byte[] writeTraces(List<List<Span>> value);

  /** throws {@linkplain IllegalArgumentException} if the dependency link couldn't be decoded */
  DependencyLink readDependencyLink(byte[] bytes);

  byte[] writeDependencyLink(DependencyLink value);

  /** throws {@linkplain IllegalArgumentException} if the dependency links couldn't be decoded */
  List<DependencyLink> readDependencyLinks(byte[] bytes);

  byte[] writeDependencyLinks(List<DependencyLink> value);
}
