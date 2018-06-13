/*
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.collector.filter;

import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.collector.CollectorMetrics;

import java.util.List;

public interface SpanFilter {
  /**
   * Process filters given a set of v1 spans. The callback should return a FilterActivatedException if filter
   * implementor wants to produce custom return codes back to the user.
   * @param spans
   * @param metrics
   * @param callback
   */
  List<Span> process(List<Span> spans, CollectorMetrics metrics, Callback<Void> callback);
}
