/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.storage.cassandra.v1;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;
import zipkin2.storage.cassandra.v1.IndexTraceId.Indexer;
import zipkin2.storage.cassandra.v1.IndexTraceId.Input;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.storage.cassandra.v1.Tables.SERVICE_NAME_INDEX;

public class IndexTraceIdTest {
  @Test void iterator_filtersEntriesWithinTraceInterval() {
    Indexer indexer = new IndexTraceId.RealIndexer(new LinkedHashMap<>(), SERVICE_NAME_INDEX);
    indexer.add(Input.create("app", 1467676800150000L, 1L));
    indexer.add(Input.create("web", 1467676800050000L, 1L));
    indexer.add(Input.create("app", 1467676800150000L, 2L));
    indexer.add(Input.create("app", 1467676800125000L, 1L));
    indexer.add(Input.create("app", 1467676800125000L, 2L));
    indexer.add(Input.create("app", 1467676800110000L, 1L));
    indexer.add(Input.create("db", 1467676800150000L, 1L));
    indexer.add(Input.create("web", 1467676800000000L, 1L));
    indexer.add(Input.create("web", 1467676800025000L, 1L));

    assertThat(indexer).containsExactlyInAnyOrder(
      Input.create("app", 1467676800110000L, 1L),
      Input.create("app", 1467676800150000L, 1L),
      Input.create("app", 1467676800125000L, 2L),
      Input.create("app", 1467676800150000L, 2L),
      Input.create("db", 1467676800150000L, 1L),
      Input.create("web", 1467676800000000L, 1L),
      Input.create("web", 1467676800050000L, 1L)
    );
  }
}
