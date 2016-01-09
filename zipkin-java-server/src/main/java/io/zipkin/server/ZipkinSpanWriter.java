/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package io.zipkin.server;

import io.zipkin.Sampler;
import io.zipkin.Span;
import io.zipkin.SpanStore;
import java.util.Iterator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ZipkinSpanWriter {

  @Autowired
  Sampler sampler;

  /**
   * Asynchronously writes spans to storage, subject to sampling policy.
   */
  @Async
  public void write(SpanStore spanStore, List<Span> spans) {
    Iterator<Span> sampled = spans.stream()
        // For portability with zipkin v1, debug always wins.
        .filter(s -> (s.debug != null && s.debug) || sampler.isSampled(s.traceId))
        .iterator();

    spanStore.accept(sampled);
  }
}
