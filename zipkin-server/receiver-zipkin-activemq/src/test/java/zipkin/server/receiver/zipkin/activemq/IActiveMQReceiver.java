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

package zipkin.server.receiver.zipkin.activemq;

import org.apache.activemq.junit.EmbeddedActiveMQBroker;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.telemetry.none.NoneTelemetryProvider;
import org.junit.ClassRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static zipkin2.TestObjects.LOTS_OF_SPANS;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60)
@ExtendWith(MockitoExtension.class)
public class IActiveMQReceiver {
  List<Span> spans = Arrays.asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1]);

  @ClassRule
  public static EmbeddedActiveMQBroker activemq = new EmbeddedActiveMQBroker();

  @Mock
  private SpanForward forward;
  private LinkedBlockingQueue<List<Span>> receivedSpans = new LinkedBlockingQueue<>();

  private ModuleManager moduleManager;

  @BeforeEach
  public void setup() throws ModuleStartException {
    activemq.start();
    moduleManager = setupModuleManager();

    final ZipkinActiveMQConfig config = new ZipkinActiveMQConfig();
    config.setConcurrency(1);
    config.setQueue("test");

    final ActiveMQHandler handler = new ActiveMQHandler(config, activemq.createConnectionFactory(), forward, moduleManager);
    handler.start();
    doAnswer(invocationOnMock -> {
      receivedSpans.add(invocationOnMock.getArgument(0, ArrayList.class));
      return null;
    }).when(forward).send(any());
  }

  @Test
  public void messageWithMultipleSpans_json2() throws Exception {
    messageWithMultipleSpans(SpanBytesEncoder.JSON_V2);
  }

  void messageWithMultipleSpans(SpanBytesEncoder encoder) throws Exception {
    byte[] message = encoder.encodeList(spans);
    activemq.pushMessage("test", message);

    final List<Span> take = receivedSpans.take();
    assertThat(take).isEqualTo(spans);
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
