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
package zipkin;

import java.util.List;

public class InMemoryDependenciesTest extends DependenciesTest<InMemorySpanStore> {

  public InMemoryDependenciesTest() {
    this.store = new InMemorySpanStore();
  }

  @Override
  public void clear() {
    store.clear();
  }

  @Override
  protected void processDependencies(List<Span> spans) {
    store.accept(spans.iterator());
  }
}
