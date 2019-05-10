/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.elasticsearch.internal;

import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import okio.Buffer;
import okio.ByteString;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin2.elasticsearch.internal.BulkCallBuilder.CheckForErrors;

public class BulkCallBuilderTest {
  @Rule public ExpectedException expectedException = ExpectedException.none();

  @Test
  public void throwRejectedExecutionExceptionWhenOverCapacity() throws IOException {
    Buffer response = new Buffer().write(ByteString.encodeUtf8("{\"took\":0,\"errors\":true,\"items\":[{\"index\":{\"_index\":\"dev-zipkin:span-2019.04.18\",\"_type\":\"span\",\"_id\":\"2511\",\"status\":429,\"error\":{\"type\":\"es_rejected_execution_exception\",\"reason\":\"rejected execution of org.elasticsearch.transport.TransportService$7@7ec1ea93 on EsThreadPoolExecutor[bulk, queue capacity = 200, org.elasticsearch.common.util.concurrent.EsThreadPoolExecutor@621571ba[Running, pool size = 4, active threads = 4, queued tasks = 200, completed tasks = 3838534]]\"}}}]}"));

    expectedException.expect(RejectedExecutionException.class);
    CheckForErrors.INSTANCE.convert(response);
  }
}
