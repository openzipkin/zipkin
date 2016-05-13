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
package zipkin.storage.guava;

import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.AsyncSpanStore;
import zipkin.storage.SpanStore;
import zipkin.storage.StorageComponent;

import static zipkin.storage.StorageAdapters.asyncToBlocking;

public abstract class GuavaStorageComponent implements StorageComponent {

  @Override public SpanStore spanStore() {
    return asyncToBlocking(asyncSpanStore());
  }

  @Override public AsyncSpanStore asyncSpanStore() {
    return GuavaStorageAdapters.guavaToAsync(guavaSpanStore());
  }

  public abstract GuavaSpanStore guavaSpanStore();

  @Override
  public AsyncSpanConsumer asyncSpanConsumer() {
    return GuavaStorageAdapters.guavaToAsync(guavaSpanConsumer());
  }

  public abstract GuavaSpanConsumer guavaSpanConsumer();
}
