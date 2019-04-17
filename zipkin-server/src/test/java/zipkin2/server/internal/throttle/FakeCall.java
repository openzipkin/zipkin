/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.server.internal.throttle;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import zipkin2.Call;
import zipkin2.Callback;

class FakeCall extends Call<Void> {
  boolean overCapacity = false;

  public void setOverCapacity(boolean isOverCapacity) {
    this.overCapacity = isOverCapacity;
  }

  @Override
  public Void execute() throws IOException {
    if (overCapacity) {
      throw new RejectedExecutionException();
    }

    return null;
  }

  @Override
  public void enqueue(Callback<Void> callback) {
    if (overCapacity) {
      callback.onError(new RejectedExecutionException());
    } else {
      callback.onSuccess(null);
    }
  }

  @Override
  public void cancel() {
  }

  @Override
  public boolean isCanceled() {
    return false;
  }

  @Override
  public Call<Void> clone() {
    return null;
  }
}
