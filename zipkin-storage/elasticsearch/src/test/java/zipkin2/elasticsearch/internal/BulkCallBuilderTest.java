/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.internal;

import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static zipkin2.elasticsearch.internal.BulkCallBuilder.CHECK_FOR_ERRORS;
import static zipkin2.elasticsearch.internal.JsonSerializers.JSON_FACTORY;

class BulkCallBuilderTest {
  @Test void throwsRejectedExecutionExceptionWhenOverCapacity() {
    String response =
      "{\"took\":0,\"errors\":true,\"items\":[{\"index\":{\"_index\":\"dev-zipkin:span-2019.04.18\",\"_type\":\"span\",\"_id\":\"2511\",\"status\":429,\"error\":{\"type\":\"es_rejected_execution_exception\",\"reason\":\"rejected execution of org.elasticsearch.transport.TransportService$7@7ec1ea93 on EsThreadPoolExecutor[bulk, queue capacity = 200, org.elasticsearch.common.util.concurrent.EsThreadPoolExecutor@621571ba[Running, pool size = 4, active threads = 4, queued tasks = 200, completed tasks = 3838534]]\"}}}]}";

    assertThatThrownBy(
      () -> CHECK_FOR_ERRORS.convert(JSON_FACTORY.createParser(response), () -> response))
      .isInstanceOf(RejectedExecutionException.class)
      .hasMessage(
        "rejected execution of org.elasticsearch.transport.TransportService$7@7ec1ea93 on EsThreadPoolExecutor[bulk, queue capacity = 200, org.elasticsearch.common.util.concurrent.EsThreadPoolExecutor@621571ba[Running, pool size = 4, active threads = 4, queued tasks = 200, completed tasks = 3838534]]");
  }

  @Test void throwsRuntimeExceptionAsRootCauseReasonWhenPresent() {
    String response = """
      {
        "error": {
          "root_cause": [
            {
              "type": "illegal_argument_exception",
              "reason": "Fielddata is disabled on text fields by default. Set fielddata=true on [spanName] in order to load fielddata in memory by uninverting the inverted index. Note that this can however use significant memory. Alternatively use a keyword field instead."
            }
          ],
          "type": "search_phase_execution_exception",
          "reason": "all shards failed",
          "phase": "query",
          "grouped": true,
          "failed_shards": [
            {
              "shard": 0,
              "index": "zipkin-2017-05-14",
              "node": "IqceAwZnSvyv0V0xALkEnQ",
              "reason": {
                "type": "illegal_argument_exception",
                "reason": "Fielddata is disabled on text fields by default. Set fielddata=true on [spanName] in order to load fielddata in memory by uninverting the inverted index. Note that this can however use significant memory. Alternatively use a keyword field instead."
              }
            }
          ]
        },
        "status": 400
      }
      """;

    assertThatThrownBy(
      () -> CHECK_FOR_ERRORS.convert(JSON_FACTORY.createParser(response), () -> response))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("Fielddata is disabled on text fields by default. Set fielddata=true on [spanName] in order to load fielddata in memory by uninverting the inverted index. Note that this can however use significant memory. Alternatively use a keyword field instead.");
  }

  /** Tests lack of a root cause won't crash */
  @Test void throwsRuntimeExceptionAsReasonWhenPresent() {
    String response = """
      {
        "error": {
          "type": "search_phase_execution_exception",
          "reason": "all shards failed",
          "phase": "query"
        },
        "status": 400
      }
      """;

    assertThatThrownBy(
      () -> CHECK_FOR_ERRORS.convert(JSON_FACTORY.createParser(response), () -> response))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("all shards failed");
  }
}
