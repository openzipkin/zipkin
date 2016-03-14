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
package zipkin.server;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import zipkin.Sampler;
import zipkin.SamplingSpanStoreConsumer;
import zipkin.Span;
import zipkin.SpanConsumer;
import zipkin.SpanStore;

/** Asynchronously writes spans to storage, subject to sampling policy. */
@Service
public class ZipkinSpanWriter implements SpanConsumer {

  final SpanConsumer delegate;

  @Autowired ZipkinSpanWriter(SpanStore spanStore, Sampler sampler) {
    this.delegate = SamplingSpanStoreConsumer.create(sampler, spanStore);
  }

  @Async
  @Override
  public void accept(List<Span> spans) {
    delegate.accept(spans);
  }
}
