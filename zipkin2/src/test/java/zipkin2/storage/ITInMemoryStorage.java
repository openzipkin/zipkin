/**
 * Copyright 2015-2018 The OpenZipkin Authors
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
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

@RunWith(Enclosed.class)
public class ITInMemoryStorage {

  public static class ITSpanStore extends zipkin2.storage.ITSpanStore {
    InMemoryStorage storage = InMemoryStorage.newBuilder().build();

    @Override protected InMemoryStorage storage() {
      return storage;
    }

    @Override public void clear() throws IOException {
      // no need.. the test rule does this
    }
  }

  public static class ITSearchEnabledFalse extends zipkin2.storage.ITSearchEnabledFalse {
    InMemoryStorage storage = InMemoryStorage.newBuilder().searchEnabled(false).build();

    @Override protected InMemoryStorage storage() {
      return storage;
    }

    @Override public void clear() throws IOException {
      // no need.. the test rule does this
    }
  }
}
