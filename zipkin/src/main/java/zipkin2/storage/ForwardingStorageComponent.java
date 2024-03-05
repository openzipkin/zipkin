/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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

  @Override public Traces traces() {
    return delegate().traces();
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
