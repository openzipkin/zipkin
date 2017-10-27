/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin2.storage.cassandra.internal.call;

import java.util.concurrent.Executor;

/** Same as {@code MoreExecutors.directExecutor()} except without a guava 18 dep */
enum DirectExecutor implements Executor {
  INSTANCE;

  @Override public void execute(Runnable command) {
    command.run();
  }

  @Override public String toString() {
    return "DirectExecutor";
  }
}
