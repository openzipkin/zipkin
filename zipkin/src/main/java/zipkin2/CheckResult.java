/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2;

import zipkin2.internal.Nullable;

/**
 * Answers the question: Are operations on this component likely to succeed?
 *
 * <p>Implementations should initialize the component if necessary. It should test a remote
 * connection, or consult a trusted source to derive the result. They should use least resources
 * possible to establish a meaningful result, and be safe to call many times, even concurrently.
 *
 * @see CheckResult#OK
 */
// @Immutable
public final class CheckResult {
  public static final CheckResult OK = new CheckResult(true, null);

  public static CheckResult failed(Throwable error) {
    return new CheckResult(false, error);
  }

  public boolean ok() {
    return ok;
  }

  /** Present when not ok */
  @Nullable
  public Throwable error() {
    return error;
  }

  final boolean ok;
  final Throwable error;

  CheckResult(boolean ok, @Nullable Throwable error) {
    this.ok = ok;
    this.error = error;
  }

  @Override
  public String toString() {
    return "CheckResult{ok=" + ok + ", " + "error=" + error + "}";
  }
}
