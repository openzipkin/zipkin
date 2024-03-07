/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.internal;

public final class ClosedComponentException extends IllegalStateException {
  static final long serialVersionUID = -4636520624634625689L;

  /** Convenience constructor that ensures the message is never null. */
  public ClosedComponentException() {
    this(null);
  }

  public ClosedComponentException(String message) {
    super(message != null ? message : "closed");
  }
}
