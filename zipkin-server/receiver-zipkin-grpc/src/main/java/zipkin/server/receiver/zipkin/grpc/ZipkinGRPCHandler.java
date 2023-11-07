/*
 * Copyright 2015-2023 The OpenZipkin Authors
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

package zipkin.server.receiver.zipkin.grpc;

import com.linecorp.armeria.common.CommonPools;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.grpc.protocol.AbstractUnsafeUnaryGrpcService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.zipkin.SpanForwardService;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.Callback;
import zipkin2.codec.SpanBytesDecoder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public class ZipkinGRPCHandler extends AbstractUnsafeUnaryGrpcService {
  private static final Logger log = LoggerFactory.getLogger(ZipkinGRPCHandler.class.getName());

  private final SpanForwardService spanForward;

  private final CounterMetrics msgDroppedIncr;
  private final CounterMetrics errorCounter;
  private final HistogramMetrics histogram;

  public ZipkinGRPCHandler(SpanForwardService spanForward, ModuleManager moduleManager) {
    this.spanForward = spanForward;

    MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
        .provider()
        .getService(MetricsCreator.class);
    histogram = metricsCreator.createHistogramMetric(
        "trace_in_latency",
        "The process latency of trace data",
        new MetricsTag.Keys("protocol"),
        new MetricsTag.Values("zipkin-kafka")
    );
    msgDroppedIncr = metricsCreator.createCounter(
        "trace_dropped_count", "The dropped number of traces",
        new MetricsTag.Keys("protocol"), new MetricsTag.Values("zipkin-grpc"));
    errorCounter = metricsCreator.createCounter(
        "trace_analysis_error_count", "The error number of trace analysis",
        new MetricsTag.Keys("protocol"), new MetricsTag.Values("zipkin-grpc")
    );
  }

  @Override
  protected CompletionStage<ByteBuf> handleMessage(ServiceRequestContext context, ByteBuf message) {
    if (!message.isReadable()) {
      msgDroppedIncr.inc();
      return CompletableFuture.completedFuture(message); // lenient on empty messages
    }

    try {
      CompletableFutureCallback result = new CompletableFutureCallback();

      // collector.accept might block so need to move off the event loop. We make sure the
      // callback is context aware to continue the trace.
      Executor executor = ServiceRequestContext.mapCurrent(
          ctx -> ctx.makeContextAware(ctx.blockingTaskExecutor()),
          CommonPools::blockingTaskExecutor);

      executor.execute(() -> {
        try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
          spanForward.send(SpanBytesDecoder.PROTO3.decodeList(message.nioBuffer()));
          result.onSuccess(null);
        } catch (Exception e) {
          log.error("Failed to handle message", e);
          errorCounter.inc();
          result.onError(e);
        }
      });
      return result;
    } finally {
      message.release();
    }
  }

  static final class CompletableFutureCallback extends CompletableFuture<ByteBuf>
      implements Callback<Void> {

    @Override public void onSuccess(Void value) {
      complete(Unpooled.EMPTY_BUFFER);
    }

    @Override public void onError(Throwable t) {
      completeExceptionally(t);
    }
  }
}
