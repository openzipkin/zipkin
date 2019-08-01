/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.storage;

import java.io.IOException;
import zipkin2.CheckResult;

/**
 * We provide a forwarding variant of the storage component for use cases such as trace decoration,
 * or throttling.
 *
 * <p>Extending this is better than extending {@link StorageComponent} directly because it reduces
 * risk of accidentally masking new methods. For example, if you extended storage component and
 * later a new feature for cache control was added, that feature would be blocked until the wrapper
 * was re-compiled. Such would be worse in most cases than not having decoration on new methods.
 *
 * @since 2.16
 */
public abstract class ForwardingStorageComponent extends StorageComponent {
  /** Constructor for use by subclasses. */
  protected ForwardingStorageComponent() {
  }

  /**
   * The delegate is a method as opposed to a field, to allow for flexibility. For example, this
   * allows you to choose to make a final or lazy field, or no field at all.
   */
  protected abstract StorageComponent delegate();

  @Override public SpanConsumer spanConsumer() {
    return delegate().spanConsumer();
  }

  @Override public SpanStore spanStore() {
    return delegate().spanStore();
  }

  @Override public AutocompleteTags autocompleteTags() {
    return delegate().autocompleteTags();
  }

  @Override public ServiceAndSpanNames serviceAndSpanNames() {
    return delegate().serviceAndSpanNames();
  }

  @Override public CheckResult check() {
    return delegate().check();
  }

  @Override public boolean isOverCapacity(Throwable e) {
    return delegate().isOverCapacity(e);
  }

  @Override public void close() throws IOException {
    delegate().close();
  }

  @Override public String toString() {
    return delegate().toString();
  }
}
