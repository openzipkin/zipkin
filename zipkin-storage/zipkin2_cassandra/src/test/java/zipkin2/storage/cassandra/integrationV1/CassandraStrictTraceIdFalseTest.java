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
package zipkin2.storage.cassandra.integrationV1;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import zipkin.Span;
import zipkin.internal.V2StorageComponent;
import zipkin.storage.StorageComponent;
import zipkin.storage.StrictTraceIdFalseTest;
import zipkin2.storage.cassandra.CassandraStorage;

import static org.assertj.core.api.Assertions.assertThat;

abstract class CassandraStrictTraceIdFalseTest extends StrictTraceIdFalseTest {

  abstract protected String keyspace();

  private CassandraStorage storage;
  private V2StorageComponent storageBeforeSwitch;

  @Before public void connect() {
    storage = storageBuilder().strictTraceId(false).keyspace(keyspace()).build();
    storageBeforeSwitch = V2StorageComponent.create(storageBuilder().keyspace(keyspace()).build());
  }

  protected abstract CassandraStorage.Builder storageBuilder();

  @Override protected final StorageComponent storage() {
    return V2StorageComponent.create(storage);
  }

  /** Ensures we can still lookup fully 128-bit traces when strict trace ID id disabled */
  @Test public void getTraces_128BitTraceId() {
    getTraces_128BitTraceId(accept128BitTrace(storageBeforeSwitch));
  }

  /** Ensures data written before strict trace ID was enabled can be read */
  @Test public void getTrace_retrievesBy128BitTraceId_afterSwitch() {
    List<Span> trace = accept128BitTrace(storageBeforeSwitch);

    assertThat(store().getRawTrace(trace.get(0).traceIdHigh, trace.get(0).traceId))
      .containsOnlyElementsOf(trace);
  }
}
