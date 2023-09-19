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

package zipkin.server.receiver.zipkin.scribe;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.telemetry.none.NoneTelemetryProvider;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.layered.TFramedTransport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;
import zipkin.server.receiver.zipkin.scribe.generated.LogEntry;
import zipkin.server.receiver.zipkin.scribe.generated.ResultCode;
import zipkin.server.receiver.zipkin.scribe.generated.Scribe;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.codec.SpanBytesEncoder;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60)
@ExtendWith(MockitoExtension.class)
public class ITScribeReceiver {
  @Mock
  private SpanForward forward;
  private LinkedBlockingQueue<List<Span>> exceptedSpans = new LinkedBlockingQueue<>();
  private NettyScribeServer server;
  private ModuleManager moduleManager;

  @BeforeEach
  public void setup() {
    doAnswer(invocationOnMock -> {
      exceptedSpans.add(invocationOnMock.getArgument(0, ArrayList.class));
      return null;
    }).when(forward).send(any());

    moduleManager = setupModuleManager();

    server = new NettyScribeServer(0, new ScribeSpanConsumer(forward, "zipkin", moduleManager));
    server.start();
  }

  @Test
  public void normal() throws Exception {
    // Java version of this sample code
    // https://github.com/facebookarchive/scribe/wiki/Logging-Messages
    TTransport transport = new TFramedTransport(new TSocket("localhost", server.port()));
    TProtocol protocol = new TBinaryProtocol(transport, false, false);
    Scribe.Iface client = new Scribe.Client(protocol);

    List<LogEntry> entries = TestObjects.TRACE.stream()
        .map(ITScribeReceiver::logEntry)
        .collect(Collectors.toList());

    transport.open();
    try {
      ResultCode code = client.Log(entries);
      assertThat(code).isEqualTo(ResultCode.OK);

      assertThat(exceptedSpans.take()).containsAll(TestObjects.TRACE);
    } finally {
      transport.close();
    }
  }

  static LogEntry logEntry(Span span) {
    return new LogEntry()
        .setCategory("zipkin")
        .setMessage(Base64.getMimeEncoder().encodeToString(SpanBytesEncoder.THRIFT.encode(span)));
  }

  private ModuleManager setupModuleManager() {
    ModuleManager moduleManager = Mockito.mock(ModuleManager.class);

    CoreModule coreModule = Mockito.spy(CoreModule.class);
    CoreModuleProvider moduleProvider = Mockito.mock(CoreModuleProvider.class);
    Whitebox.setInternalState(coreModule, "loadedProvider", moduleProvider);

    TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
    NoneTelemetryProvider noneTelemetryProvider = Mockito.mock(NoneTelemetryProvider.class);
    Whitebox.setInternalState(telemetryModule, "loadedProvider", noneTelemetryProvider);
    Mockito.when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);

    Mockito.when(noneTelemetryProvider.getService(MetricsCreator.class))
        .thenReturn(new MetricsCreatorNoop());

    return moduleManager;
  }

}
