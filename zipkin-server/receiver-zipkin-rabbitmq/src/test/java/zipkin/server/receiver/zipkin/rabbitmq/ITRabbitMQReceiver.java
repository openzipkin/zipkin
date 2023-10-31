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

package zipkin.server.receiver.zipkin.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
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
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;
import zipkin.server.receiver.zipkin.core.ZipkinReceiverCoreProvider;
import zipkin.server.receriver.zipkin.rabbitmq.ZipkinRabbitMQConfig;
import zipkin.server.receriver.zipkin.rabbitmq.ZipkinRabbitMQProvider;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static zipkin2.TestObjects.LOTS_OF_SPANS;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60)
@ExtendWith(MockitoExtension.class)
public class ITRabbitMQReceiver {
  @RegisterExtension RabbitMQExtension rabbitMQ = new RabbitMQExtension();

  List<Span> spans = asList(LOTS_OF_SPANS[0], LOTS_OF_SPANS[1]);

  private ModuleManager moduleManager;
  @Mock
  private SpanForward forward;
  private ZipkinRabbitMQConfig config = new ZipkinRabbitMQConfig();
  private LinkedBlockingQueue<List<Span>> exceptedSpans = new LinkedBlockingQueue<>();
  Connection connection;

  @BeforeEach
  public void setup() throws ModuleStartException, IOException, TimeoutException {
    config.setAddresses(Collections.singletonList(rabbitMQ.host() + ":" + rabbitMQ.port()));
    config.setQueue("test");

    moduleManager = setupModuleManager(forward);

    final ZipkinRabbitMQProvider provider = new ZipkinRabbitMQProvider();
    provider.setManager(moduleManager);
    Whitebox.setInternalState(provider, ZipkinRabbitMQConfig.class, config);
    provider.prepare();
    provider.start();
    doAnswer(invocationOnMock -> {
      exceptedSpans.add(invocationOnMock.getArgument(0, ArrayList.class));
      return null;
    }).when(forward).send(any());
    provider.notifyAfterCompleted();

    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(rabbitMQ.host());
    factory.setPort(rabbitMQ.port());
    connection = factory.newConnection();
  }

  @Test
  void messageWithMultipleSpans_json2() throws Exception {
    messageWithMultipleSpans(SpanBytesEncoder.JSON_V2);
  }

  void messageWithMultipleSpans(SpanBytesEncoder encoder)
      throws Exception {
    byte[] message = encoder.encodeList(spans);

    produceSpans(message, "test");

    assertThat(exceptedSpans.take()).containsAll(spans);
  }

  void produceSpans(byte[] spans, String queue) throws Exception {
    Channel channel = null;
    try {
      channel = connection.createChannel();
      channel.basicPublish("", queue, null, spans);
    } finally {
      if (channel != null) channel.close();
    }
  }

  private ModuleManager setupModuleManager(SpanForward forward) {
    ModuleManager moduleManager = Mockito.mock(ModuleManager.class);

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
