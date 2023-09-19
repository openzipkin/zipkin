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

package zipkin.server.receiver.zipkin.http;

import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
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
import zipkin.server.core.CoreModuleConfig;
import zipkin.server.core.services.ZipkinConfigService;
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.codec.SpanBytesEncoder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static zipkin2.TestObjects.CLIENT_SPAN;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60)
@ExtendWith(MockitoExtension.class)
public class ITHTTPReceiver {
  private static final int port = 8000;
  private ModuleManager moduleManager;
  @Mock
  private SpanForward forward;
  private LinkedBlockingQueue<List<Span>> spans = new LinkedBlockingQueue<>();

  @BeforeEach
  public void setup() throws ModuleStartException {
    final ZipkinHTTPReceiverConfig config = new ZipkinHTTPReceiverConfig();
    config.setRestHost("0.0.0.0");
    config.setRestPort(port);
    config.setRestContextPath("/");
    config.setRestIdleTimeOut(1000);
    config.setRestMaxThreads(2);
    config.setRestAcceptQueueSize(10);

    moduleManager = setupModuleManager();

    final ZipkinHTTPReceiverProvider provider = new ZipkinHTTPReceiverProvider();
    provider.setManager(moduleManager);
    Whitebox.setInternalState(provider, ZipkinHTTPReceiverConfig.class, config);
    provider.prepare();
    provider.start();
    doAnswer(invocationOnMock -> {
      spans.add(invocationOnMock.getArgument(0, ArrayList.class));
      return null;
    }).when(forward).send(any());
    Whitebox.setInternalState(provider, SpanForward.class, forward);
    provider.notifyAfterCompleted();
  }

  @Test
  public void test() throws Exception {
    final byte[] spansBuffer = SpanBytesEncoder.JSON_V2.encodeList(Arrays.asList(TestObjects.CLIENT_SPAN));
    URL url = new URL("http://localhost:" + port + "/api/v2/spans");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    try (OutputStream os = connection.getOutputStream()) {
      os.write(spansBuffer, 0, spansBuffer.length);
    }

    int responseCode = connection.getResponseCode();
    if (responseCode != HttpURLConnection.HTTP_OK) { // success
      BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
      String inputLine;
      StringBuffer response = new StringBuffer();

      while ((inputLine = in.readLine()) != null) {
        response.append(inputLine);
      }
      throw new IllegalStateException("POST request failed: " + response.toString());
    }

    assertThat(spans.take()).containsExactly(CLIENT_SPAN);
  }

  private ModuleManager setupModuleManager() {
    ModuleManager moduleManager = Mockito.mock(ModuleManager.class);

    CoreModule coreModule = Mockito.spy(CoreModule.class);
    CoreModuleProvider moduleProvider = Mockito.mock(CoreModuleProvider.class);
    Whitebox.setInternalState(coreModule, "loadedProvider", moduleProvider);
    Mockito.when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);

    TelemetryModule telemetryModule = Mockito.spy(TelemetryModule.class);
    NoneTelemetryProvider noneTelemetryProvider = Mockito.mock(NoneTelemetryProvider.class);
    Whitebox.setInternalState(telemetryModule, "loadedProvider", noneTelemetryProvider);
    Mockito.when(moduleManager.find(TelemetryModule.NAME)).thenReturn(telemetryModule);

    Mockito.when(moduleProvider.getService(ConfigService.class))
        .thenReturn(new ZipkinConfigService(new CoreModuleConfig(), moduleProvider));
    Mockito.when(noneTelemetryProvider.getService(MetricsCreator.class))
        .thenReturn(new MetricsCreatorNoop());

    return moduleManager;
  }

}
