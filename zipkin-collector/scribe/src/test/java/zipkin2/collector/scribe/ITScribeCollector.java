/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.scribe;

import com.linecorp.armeria.common.CommonPools;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.scribe.generated.LogEntry;
import zipkin2.collector.scribe.generated.ResultCode;
import zipkin2.collector.scribe.generated.Scribe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ITScribeCollector {
  static Collector collector;
  static CollectorMetrics metrics;
  static NettyScribeServer server;

  @BeforeAll static void startServer() {
    collector = mock(Collector.class);
    doAnswer(invocation -> {
      Callback<Void> callback = invocation.getArgument(1);
      callback.onSuccess(null);
      return null;
    }).when(collector).accept(any(), any(), any());

    metrics = mock(CollectorMetrics.class);

    server = new NettyScribeServer(0, new ScribeSpanConsumer(collector, metrics, "zipkin"));
    server.start();
  }

  @AfterAll static void stopServer() {
    server.close();
  }

  @Test void normal() throws Exception {
    // Java version of this sample code
    // https://github.com/facebookarchive/scribe/wiki/Logging-Messages
    TTransport transport = new TFramedTransport(new TSocket("localhost", server.port()));
    TProtocol protocol = new TBinaryProtocol(transport, false, false);
    Scribe.Iface client = new Scribe.Client(protocol);

    List<LogEntry> entries = TestObjects.TRACE.stream()
      .map(ITScribeCollector::logEntry)
      .collect(Collectors.toList());

    transport.open();
    try {
      ResultCode code = client.Log(entries);
      assertThat(code).isEqualTo(ResultCode.OK);

      code = client.Log(entries);
      assertThat(code).isEqualTo(ResultCode.OK);
    } finally {
      transport.close();
    }

    verify(collector, times(2)).accept(eq(TestObjects.TRACE), any(),
      eq(CommonPools.blockingTaskExecutor()));
    verify(metrics, times(2)).incrementMessages();
  }

  static LogEntry logEntry(Span span) {
    return new LogEntry()
      .setCategory("zipkin")
      .setMessage(Base64.getMimeEncoder().encodeToString(SpanBytesEncoder.THRIFT.encode(span)));
  }
}
