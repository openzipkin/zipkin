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

package zipkin.server.receiver.zipkin.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.zipkin.SpanForwardService;
import org.apache.skywalking.oap.server.receiver.zipkin.ZipkinReceiverModule;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.telemetry.none.NoneTelemetryProvider;
import org.junit.jupiter.api.AfterEach;
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
import zipkin2.Span;
import zipkin2.TestObjects;
import zipkin2.codec.SpanBytesEncoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static zipkin2.TestObjects.CLIENT_SPAN;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60)
@ExtendWith(MockitoExtension.class)
public class ITKafkaReceiver {
  @RegisterExtension KafkaExtension kafka = new KafkaExtension();

  private ModuleManager moduleManager;
  @Mock
  private SpanForward forward;
  private ZipkinKafkaReceiverConfig config = new ZipkinKafkaReceiverConfig();
  private LinkedBlockingQueue<List<Span>> spans = new LinkedBlockingQueue<>();

  KafkaProducer<byte[], byte[]> producer;

  @BeforeEach
  public void setup() throws ModuleStartException {
    Properties kafkaClientConf = new Properties();
    kafkaClientConf.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServer());
    producer = new KafkaProducer<>(kafkaClientConf, new ByteArraySerializer(), new ByteArraySerializer());

    config.setKafkaBootstrapServers(kafka.bootstrapServer());
    config.setKafkaHandlerThreadPoolSize(2);
    config.setKafkaHandlerThreadPoolQueueSize(100);

    moduleManager = setupModuleManager(forward);

    final ZipkinKafkaReceiverProvider provider = new ZipkinKafkaReceiverProvider();
    provider.setManager(moduleManager);
    Whitebox.setInternalState(provider, ZipkinKafkaReceiverConfig.class, config);
    provider.prepare();
    provider.start();
    doAnswer(invocationOnMock -> {
      spans.add(invocationOnMock.getArgument(0, ArrayList.class));
      return null;
    }).when(forward).send(any());
    provider.notifyAfterCompleted();

    kafka.prepareTopics(config.getKafkaTopic(), 1);
  }

  @Test
  public void test() throws InterruptedException {
    final byte[] spansBuffer = SpanBytesEncoder.JSON_V2.encodeList(Arrays.asList(TestObjects.CLIENT_SPAN));
    produceSpans(spansBuffer, config.getKafkaTopic());

    assertThat(spans.take()).containsExactly(CLIENT_SPAN);
  }

  void produceSpans(byte[] spans, String topic) {
    produceSpans(spans, topic, 0);
  }

  void produceSpans(byte[] spans, String topic, Integer partition) {
    producer.send(new ProducerRecord<>(topic, partition, null, spans));
    producer.flush();
  }

  @AfterEach
  public void tearDown() {
    if (producer != null) {
      producer.close();
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
