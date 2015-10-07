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
package io.zipkin.server;

import com.twitter.zipkin.storage.SpanStore;
import com.twitter.zipkin.storage.SpanStoreSpec;
import io.zipkin.interop.ScalaSpanStoreAdapter;
import io.zipkin.server.InMemorySpanStore;

public class InMemoryScalaSpanStoreTest extends SpanStoreSpec {
  private InMemorySpanStore mem = new InMemorySpanStore();

  public SpanStore store() {
    return new ScalaSpanStoreAdapter(mem);
  }

  public void clear() {
    mem.clear();
  }
}
