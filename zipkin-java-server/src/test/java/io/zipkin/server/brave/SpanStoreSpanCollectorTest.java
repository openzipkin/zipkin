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
package io.zipkin.server.brave;

import com.twitter.zipkin.gen.Annotation;
import com.twitter.zipkin.gen.Endpoint;
import com.twitter.zipkin.gen.Span;
import io.zipkin.server.InMemorySpanStore;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SpanStoreSpanCollectorTest {

  private InMemorySpanStore spanStore = new InMemorySpanStore();
  private SpanStoreSpanCollector collector = new SpanStoreSpanCollector(spanStore);

  @Test
  public void addOne() {
    Span span = newSpan(1234L, 1234L, "foo", "value", "service");
    collector.collect(span);
    collector.flush();
    assertEquals("[service]", spanStore.getServiceNames().toString());
  }

  @Test
  public void addMany() {
    for (int i = 0; i < 500; i++) {
      Span span = newSpan(1234L, 1234L + i, "foo", "value", "service");
      collector.collect(span);
    }
    collector.flush();
    assertEquals("[service]", spanStore.getServiceNames().toString());
    assertEquals(500, spanStore.getTracesByIds(Arrays.asList(1234L)).get(0).size());
  }

  private static Span newSpan(long traceId, long id, String spanName, String value, String service) {
    Span span = new Span();
    span.setId(id);
    span.setTrace_id(traceId);
    span.setParent_id(traceId);
    span.setName(spanName);
    Annotation annotation = new Annotation();
    annotation.setHost(new Endpoint(0, (short) 80, service));
    annotation.setValue(value);
    span.addToAnnotations(annotation);
    return span;
  }
}
