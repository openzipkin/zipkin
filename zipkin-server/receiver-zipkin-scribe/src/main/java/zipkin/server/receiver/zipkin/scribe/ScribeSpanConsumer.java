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
package zipkin.server.receiver.zipkin.scribe;

import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.apache.thrift.async.AsyncMethodCallback;
import zipkin.server.receiver.zipkin.scribe.generated.LogEntry;
import zipkin.server.receiver.zipkin.scribe.generated.ResultCode;
import zipkin.server.receiver.zipkin.scribe.generated.Scribe;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class ScribeSpanConsumer implements Scribe.AsyncIface {
  private final SpanForward spanForward;
  private final String category;

  private final HistogramMetrics histogram;

  ScribeSpanConsumer(SpanForward spanForward, String category, ModuleManager moduleManager) {
    this.spanForward = spanForward;
    this.category = category;

    MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
        .provider()
        .getService(MetricsCreator.class);
    histogram = metricsCreator.createHistogramMetric(
        "trace_in_latency",
        "The process latency of trace data",
        new MetricsTag.Keys("protocol"),
        new MetricsTag.Values("zipkin-kafka")
    );
  }

  @Override
  public void Log(List<LogEntry> messages, AsyncMethodCallback<ResultCode> resultHandler) {
    try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
      List<Span> spans = new ArrayList<>();
      try {
        for (LogEntry logEntry : messages) {
          if (!category.equals(logEntry.category)) continue;
          byte[] bytes = logEntry.message.getBytes(StandardCharsets.ISO_8859_1);
          bytes = Base64.getMimeDecoder().decode(bytes); // finagle-zipkin uses mime encoding
          spans.add(SpanBytesDecoder.THRIFT.decodeOne(bytes));
        }
      } catch (RuntimeException e) {
        resultHandler.onError(e);
        return;
      }

      spanForward.send(spans);
      resultHandler.onComplete(ResultCode.OK);
    }
  }
}
