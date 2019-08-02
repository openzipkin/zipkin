/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.elasticsearch.internal;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static zipkin2.elasticsearch.internal.BulkCallBuilder.CHECK_FOR_ERRORS;
import static zipkin2.elasticsearch.internal.JsonSerializers.JSON_FACTORY;

public class BulkCallBuilderTest {
  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test public void throwsRejectedExecutionExceptionWhenOverCapacity() throws IOException {
    String response =
      "{\"took\":0,\"errors\":true,\"items\":[{\"index\":{\"_index\":\"dev-zipkin:span-2019.04.18\",\"_type\":\"span\",\"_id\":\"2511\",\"status\":429,\"error\":{\"type\":\"es_rejected_execution_exception\",\"reason\":\"rejected execution of org.elasticsearch.transport.TransportService$7@7ec1ea93 on EsThreadPoolExecutor[bulk, queue capacity = 200, org.elasticsearch.common.util.concurrent.EsThreadPoolExecutor@621571ba[Running, pool size = 4, active threads = 4, queued tasks = 200, completed tasks = 3838534]]\"}}}]}";

    expectedException.expect(RejectedExecutionException.class);
    expectedException.expectMessage(
      "rejected execution of org.elasticsearch.transport.TransportService$7@7ec1ea93 on EsThreadPoolExecutor[bulk, queue capacity = 200, org.elasticsearch.common.util.concurrent.EsThreadPoolExecutor@621571ba[Running, pool size = 4, active threads = 4, queued tasks = 200, completed tasks = 3838534]]");
    CHECK_FOR_ERRORS.convert(JSON_FACTORY.createParser(response), () -> response);
  }

  @Test public void throwsRuntimeExceptionAsReasonWhenPresent() throws IOException {
    String response =
      "{\"error\":{\"root_cause\":[{\"type\":\"illegal_argument_exception\",\"reason\":\"Fielddata is disabled on text fields by default. Set fielddata=true on [spanName] in order to load fielddata in memory by uninverting the inverted index. Note that this can however use significant memory. Alternatively use a keyword field instead.\"}],\"type\":\"search_phase_execution_exception\",\"reason\":\"all shards failed\",\"phase\":\"query\",\"grouped\":true,\"failed_shards\":[{\"shard\":0,\"index\":\"zipkin-2017-05-14\",\"node\":\"IqceAwZnSvyv0V0xALkEnQ\",\"reason\":{\"type\":\"illegal_argument_exception\",\"reason\":\"Fielddata is disabled on text fields by default. Set fielddata=true on [spanName] in order to load fielddata in memory by uninverting the inverted index. Note that this can however use significant memory. Alternatively use a keyword field instead.\"}}]},\"status\":400}";

    expectedException.expect(RuntimeException.class);
    expectedException.expectMessage(
      "Fielddata is disabled on text fields by default. Set fielddata=true on [spanName] in order to load fielddata in memory by uninverting the inverted index. Note that this can however use significant memory. Alternatively use a keyword field instead.");
    CHECK_FOR_ERRORS.convert(JSON_FACTORY.createParser(response), () -> response);
  }
}
