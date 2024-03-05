/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.throttle;

import java.util.concurrent.RejectedExecutionException;
import zipkin2.Call;
import zipkin2.Callback;

class FakeCall extends Call<Void> {
  boolean overCapacity = false;

  void setOverCapacity(boolean isOverCapacity) {
    this.overCapacity = isOverCapacity;
  }

  @Override public Void execute() {
    if (overCapacity) throw new RejectedExecutionException();
    return null;
  }

  @Override public void enqueue(Callback<Void> callback) {
    if (overCapacity) {
      callback.onError(new RejectedExecutionException());
    } else {
      callback.onSuccess(null);
    }
  }

  @Override public void cancel() {
  }

  @Override public boolean isCanceled() {
    return false;
  }

  @Override public Call<Void> clone() {
    return null;
  }
}
