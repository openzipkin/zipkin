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
package zipkin.junit.v2;

import java.io.IOException;
import org.junit.Rule;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import zipkin.internal.V2StorageComponent;
import zipkin.junit.ZipkinRule;
import zipkin.storage.StorageComponent;

@RunWith(Enclosed.class)
public class ITHttpV2Storage {

  public static class DependenciesTest extends zipkin.storage.DependenciesTest {
    @Rule public ZipkinRule server = new ZipkinRule();
    HttpV2Storage storage = new HttpV2Storage(server.httpUrl());

    @Override protected StorageComponent storage() {
      return V2StorageComponent.create(storage);
    }

    @Override public void clear() {
      // no need.. the test rule does this
    }
  }

  public static class SpanStoreTest extends zipkin.storage.SpanStoreTest {
    @Rule public ZipkinRule server = new ZipkinRule();
    HttpV2Storage storage = new HttpV2Storage(server.httpUrl());

    @Override protected StorageComponent storage() {
      return V2StorageComponent.create(storage);
    }

    @Override public void clear() throws IOException {
      // no need.. the test rule does this
    }
  }
}
