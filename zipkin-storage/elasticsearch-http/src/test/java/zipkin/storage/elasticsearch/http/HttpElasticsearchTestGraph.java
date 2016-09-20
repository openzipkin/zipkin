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
package zipkin.storage.elasticsearch.http;

import com.google.common.collect.ImmutableList;
import org.junit.AssumptionViolatedException;
import zipkin.Component.CheckResult;
import zipkin.internal.LazyCloseable;
import zipkin.storage.elasticsearch.ElasticsearchStorage;

public enum HttpElasticsearchTestGraph {
  INSTANCE;

  public final LazyCloseable<ElasticsearchStorage> storage =
      new LazyCloseable<ElasticsearchStorage>() {
        public AssumptionViolatedException ex;

        @Override protected ElasticsearchStorage compute() {
          if (ex != null) throw ex;
          ElasticsearchStorage result =
              ElasticsearchStorage.builder(new HttpClient.Builder()
                  .hosts(ImmutableList.of("http://localhost:9200")).flushOnWrites(true))
                  .index("test_zipkin_http").build();
          CheckResult check = result.check();
          if (check.ok) return result;
          throw ex = new AssumptionViolatedException(check.exception.getMessage(), check.exception);
        }
      };
}
