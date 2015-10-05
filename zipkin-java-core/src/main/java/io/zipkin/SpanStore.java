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

import java.io.Closeable;
import java.util.List;

public interface SpanStore extends Closeable {

  /**
   * Sinks the given spans, ignoring duplicate annotations.
   */
  void accept(List<Span> spans);

  List<List<Span>> getTraces(QueryRequest request);

  List<List<Span>> getTracesByIds(List<Long> traceIds);

  List<String> getServiceNames();

  List<String> getSpanNames(String serviceName);

  @Override
  void close();
}
