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
package zipkin.storage.elasticsearch;

import org.junit.AssumptionViolatedException;
import zipkin.Component.CheckResult;
import zipkin.internal.LazyCloseable;

enum ElasticsearchTestGraph {
  INSTANCE;

  static {
    ElasticsearchStorage.FLUSH_ON_WRITES = true;
  }

  final LazyCloseable<ElasticsearchStorage> storage = new LazyCloseable<ElasticsearchStorage>() {
    public AssumptionViolatedException ex;

    @Override protected ElasticsearchStorage compute() {
      if (ex != null) throw ex;
      ElasticsearchStorage result = new ElasticsearchStorage.Builder().build();
      CheckResult check = result.check();
      if (check.ok) return result;
      throw ex = new AssumptionViolatedException(check.exception.getMessage());
    }
  };
}
