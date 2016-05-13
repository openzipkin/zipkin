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

import zipkin.internal.Lazy;

public abstract class LazyGuavaStorageComponent<S extends GuavaSpanStore, C extends GuavaSpanConsumer>
    extends GuavaStorageComponent {

  /** This will be called once and remembered for {@link #guavaSpanStore()}. */
  protected abstract S computeGuavaSpanStore();

  /** This will be called once and remembered for {@link #guavaSpanConsumer()}. */
  protected abstract C computeGuavaSpanConsumer();

  private final Lazy<S> lazyGuavaSpanStore = new Lazy<S>() {

    @Override protected S compute() {
      return computeGuavaSpanStore();
    }

    @Override public String toString() {
      return "LazyGuavaSpanStore";
    }
  };

  @Override
  public final S guavaSpanStore() {
    return lazyGuavaSpanStore.get();
  }

  private final Lazy<C> lazyGuavaSpanConsumer = new Lazy<C>() {

    @Override protected C compute() {
      return computeGuavaSpanConsumer();
    }

    @Override public String toString() {
      return "LazyGuavaSpanConsumer";
    }
  };

  @Override
  public final C guavaSpanConsumer() {
    return lazyGuavaSpanConsumer.get();
  }
}
