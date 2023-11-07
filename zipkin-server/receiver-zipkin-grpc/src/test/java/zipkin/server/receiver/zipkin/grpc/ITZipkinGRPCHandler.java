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

import com.linecorp.armeria.client.Clients;
import com.linecorp.armeria.client.grpc.protocol.UnaryGrpcClient;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegister;
import org.apache.skywalking.oap.server.core.server.HTTPHandlerRegisterImpl;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.library.server.http.HTTPServer;
import org.apache.skywalking.oap.server.library.server.http.HTTPServerConfig;
import org.apache.skywalking.oap.server.receiver.zipkin.SpanForwardService;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverModule;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.telemetry.none.NoneTelemetryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;
import zipkin.server.core.services.HTTPConfigurableServer;
import zipkin.server.receiver.zipkin.core.ZipkinReceiverCoreProvider;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.codec.SpanBytesEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60)
@ExtendWith(MockitoExtension.class)
public class ITZipkinGRPCHandler {

  private static final int port = 8000;
  private ModuleManager moduleManager;
  @Mock
  private SpanForward forward;
  private LinkedBlockingQueue<List<Span>> spans = new LinkedBlockingQueue<>();

  @BeforeEach
  public void setup() throws ModuleStartException {
    final HTTPServer httpServer = new HTTPConfigurableServer(HTTPServerConfig.builder().host("0.0.0.0").port(port).contextPath("/").build());
    httpServer.initialize();
    moduleManager = setupModuleManager(httpServer, forward);

    final ZipkinGRPCProvider provider = new ZipkinGRPCProvider();
    provider.setManager(moduleManager);
    final ZipkinGRPCReceiverConfig config = new ZipkinGRPCReceiverConfig();
    Whitebox.setInternalState(provider, ZipkinGRPCReceiverConfig.class, config);
    provider.prepare();
    provider.start();
    httpServer.start();
    doAnswer(invocationOnMock -> {
      spans.add(invocationOnMock.getArgument(0, ArrayList.class));
      return null;
    }).when(forward).send(any());
    provider.notifyAfterCompleted();
  }

  @Test
  public void test() throws Exception {
    UnaryGrpcClient client = Clients.newClient("gproto+http://127.0.0.1:" + port, UnaryGrpcClient.class);
    final CompletableFuture<byte[]> result = client.execute("zipkin.proto3.SpanService/Report", SpanBytesEncoder.PROTO3.encodeList(TestObjects.TRACE));
    result.get();
    assertThat(spans.take()).containsAll(TestObjects.TRACE);
  }

  private ModuleManager setupModuleManager(HTTPServer httpServer, SpanForward forward) {
    ModuleManager moduleManager = Mockito.mock(ModuleManager.class);

    CoreModule coreModule = Mockito.spy(CoreModule.class);
    CoreModuleProvider moduleProvider = Mockito.mock(CoreModuleProvider.class);
    Whitebox.setInternalState(coreModule, "loadedProvider", moduleProvider);
    Mockito.when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);
    Mockito.when(coreModule.provider().getService(HTTPHandlerRegister.class)).thenReturn(new HTTPHandlerRegisterImpl(httpServer));

    final ZipkinReceiverModule zipkinReceiverModule = Mockito.spy(ZipkinReceiverModule.class);
    final ZipkinReceiverCoreProvider receiverProvider = Mockito.mock(ZipkinReceiverCoreProvider.class);
    Whitebox.setInternalState(zipkinReceiverModule, "loadedProvider", receiverProvider);
    Mockito.when(moduleManager.find(ZipkinReceiverModule.NAME)).thenReturn(zipkinReceiverModule);
    Mockito.when(zipkinReceiverModule.provider().getService(SpanForwardService.class)).thenReturn(forward);

    TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
    NoneTelemetryProvider noneTelemetryProvider = Mockito.mock(NoneTelemetryProvider.class);
    Whitebox.setInternalState(telemetryModule, "loadedProvider", noneTelemetryProvider);
    Mockito.when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);

    Mockito.when(noneTelemetryProvider.getService(MetricsCreator.class))
        .thenReturn(new MetricsCreatorNoop());

    return moduleManager;
  }

}
