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
package zipkin.internal;

import java.io.Closeable;
import java.io.IOException;

import static zipkin.internal.Util.checkArgument;

public abstract class LazyCloseable<T> extends Lazy<T> implements Closeable {

  @Override
  public void close() throws IOException {
    T maybeNull = maybeNull();
    if (maybeNull != null) {
      checkArgument(maybeNull instanceof Closeable, "Override close() to close " + maybeNull);
      ((Closeable) maybeNull).close();
    }
  }

  /** Used to conditionally close resources. No-op if the value hasn't been computed, yet. */
  protected final T maybeNull() {
    return instance;
  }
}
