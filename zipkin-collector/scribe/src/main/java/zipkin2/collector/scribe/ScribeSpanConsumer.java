/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.scribe;

import com.linecorp.armeria.common.CommonPools;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.apache.thrift.async.AsyncMethodCallback;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.scribe.generated.LogEntry;
import zipkin2.collector.scribe.generated.ResultCode;
import zipkin2.collector.scribe.generated.Scribe;

final class ScribeSpanConsumer implements Scribe.AsyncIface {
  final Collector collector;
  final CollectorMetrics metrics;
  final String category;

  ScribeSpanConsumer(Collector collector, CollectorMetrics metrics, String category) {
    this.collector = collector;
    this.metrics = metrics;
    this.category = category;
  }

  @Override
  public void Log(List<LogEntry> messages, AsyncMethodCallback<ResultCode> resultHandler) {
    metrics.incrementMessages();
    List<Span> spans = new ArrayList<>();
    int byteCount = 0;
    try {
      for (LogEntry logEntry : messages) {
        if (!category.equals(logEntry.category)) continue;
        byte[] bytes = logEntry.message.getBytes(StandardCharsets.ISO_8859_1);
        bytes = Base64.getMimeDecoder().decode(bytes); // finagle-zipkin uses mime encoding
        byteCount += bytes.length;
        spans.add(SpanBytesDecoder.THRIFT.decodeOne(bytes));
      }
    } catch (RuntimeException e) {
      metrics.incrementMessagesDropped();
      resultHandler.onError(e);
      return;
    } finally {
      metrics.incrementBytes(byteCount);
    }

    collector.accept(spans, new Callback<Void>() {
      @Override public void onSuccess(Void value) {
        resultHandler.onComplete(ResultCode.OK);
      }

      @Override public void onError(Throwable t) {
        Exception error = t instanceof Exception e ? e : new RuntimeException(t);
        resultHandler.onError(error);
      }
    // Collectors may not be asynchronous so switch to blocking executor here.
    }, CommonPools.blockingTaskExecutor());
  }
}
