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
package zipkin.storage.cassandra3;

import zipkin.storage.cassandra3.Dse5Storage;
import org.junit.AssumptionViolatedException;
import zipkin.Component.CheckResult;
import zipkin.internal.LazyCloseable;

enum Dse5TestGraph {
  INSTANCE;

  final LazyCloseable<Dse5Storage> storage = new LazyCloseable<Dse5Storage>() {
    AssumptionViolatedException ex = null;

    @Override protected Dse5Storage compute() {
      if (ex != null) throw ex;
      Dse5Storage result = Dse5Storage.builder().keyspace("test_zipkin3").build();
      CheckResult check = result.check();
      if (check.ok) return result;
      throw ex = new AssumptionViolatedException(check.exception.getMessage());
    }
  };
}
