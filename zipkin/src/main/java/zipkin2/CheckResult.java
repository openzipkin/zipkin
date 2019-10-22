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
