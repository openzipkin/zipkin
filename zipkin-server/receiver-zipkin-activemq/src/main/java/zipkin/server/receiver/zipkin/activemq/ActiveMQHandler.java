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

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.Closeable;
import org.apache.activemq.transport.TransportListener;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import zipkin2.CheckResult;
import zipkin2.Span;
import zipkin2.SpanBytesDecoderDetector;
import zipkin2.codec.BytesDecoder;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.QueueReceiver;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ActiveMQHandler implements TransportListener, MessageListener, Closeable {
  private static final Logger log = LoggerFactory.getLogger(ActiveMQHandler.class.getName());

  private final ZipkinActiveMQConfig config;
  private final SpanForward spanForward;
  private final ActiveMQConnectionFactory connectionFactory;

  static final CheckResult
      CLOSED = CheckResult.failed(new IllegalStateException("Collector intentionally closed")),
      INTERRUPTION = CheckResult.failed(new IOException("Recoverable error on ActiveMQ connection"));

  private final CounterMetrics msgDroppedIncr;
  private final CounterMetrics errorCounter;
  private final HistogramMetrics histogram;

  private ActiveMQConnection connection;
  final Map<QueueSession, QueueReceiver> sessionToReceiver = new LinkedHashMap<>();

  volatile CheckResult checkResult = CheckResult.OK;

  public ActiveMQHandler(ZipkinActiveMQConfig config, SpanForward spanForward, ModuleManager moduleManager) {
    this(config, createConnectionFactory(config), spanForward, moduleManager);
  }

  public ActiveMQHandler(ZipkinActiveMQConfig config, ActiveMQConnectionFactory connectionFactory, SpanForward spanForward, ModuleManager moduleManager) {
    this.config = config;
    this.spanForward = spanForward;
    this.connectionFactory = connectionFactory;

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
        new MetricsTag.Keys("protocol"), new MetricsTag.Values("zipkin-kafka"));
    errorCounter = metricsCreator.createCounter(
        "trace_analysis_error_count", "The error number of trace analysis",
        new MetricsTag.Keys("protocol"), new MetricsTag.Values("zipkin-kafka")
    );
  }

  public void start() {
    final ActiveMQConnection connection;
    try {
      connection = (ActiveMQConnection) connectionFactory.createQueueConnection();
      connection.start();
    } catch (JMSException e) {
      throw new RuntimeException("Unable to establish connection to ActiveMQ broker: ", e);
    }
    this.connection = connection;
    this.connection.addTransportListener(this);

    try {
      for (int i = 0; i < config.getConcurrency(); i++) {
        this.registerInNewSession(connection, config.getQueue());
      }
    } catch (JMSException e) {
      try {
        connection.close();
      } catch (JMSException ignored) {
        // EmptyCatch ignored
      }
      throw new RuntimeException("Unable to create queueReceiver(" + config.getQueue() + "): ", e);
    }
  }

  @Override
  public void onMessage(Message message) {
    byte[] serialized;
    try {
      if (message instanceof BytesMessage) {
        BytesMessage bytesMessage = (BytesMessage) message;
        serialized = new byte[(int) bytesMessage.getBodyLength()];
        bytesMessage.readBytes(serialized);
      } else if (message instanceof TextMessage) {
        String text = ((TextMessage) message).getText();
        serialized = text.getBytes(UTF_8);
      } else {
        msgDroppedIncr.inc();
        return;
      }
    } catch (Exception e) {
      errorCounter.inc();
      log.warn("Error reading message", e);
      return;
    }

    if (serialized.length == 0) return; // lenient on empty messages

    try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
      BytesDecoder<Span> decoder;
      List<Span> out = new ArrayList<>();
      try {
        decoder = SpanBytesDecoderDetector.decoderForListMessage(serialized);
        decoder.decodeList(serialized, out);
      } catch (RuntimeException | Error e) {
        return;
      }
      spanForward.send(out);
    }
  }

  void registerInNewSession(ActiveMQConnection connection, String queue) throws JMSException {
    // Pass redundant info as we can't use default method in activeMQ
    QueueSession session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
    // No need to do anything on ActiveMQ side as physical queues are created on demand
    Queue destination = session.createQueue(queue);
    QueueReceiver receiver = session.createReceiver(destination);
    receiver.setMessageListener(this);
    sessionToReceiver.put(session, receiver);
  }

  @Override
  public void close() throws JMSException {
    if (checkResult == CLOSED) return;
    checkResult = CLOSED;
    connection.removeTransportListener(this);
    try {
      for (Map.Entry<QueueSession, QueueReceiver> sessionReceiver : sessionToReceiver.entrySet()) {
        sessionReceiver.getValue().setMessageListener(null); // deregister this
        sessionReceiver.getKey().close();
      }
      connection.close();
    } catch (JMSException ignored) {
      // EmptyCatch ignored
    }
  }

  @Override
  public void onCommand(Object o) {
  }

  @Override
  public void onException(IOException e) {
    checkResult = CheckResult.failed(e);
  }

  @Override
  public void transportInterupted() {
    checkResult = INTERRUPTION;
  }

  @Override
  public void transportResumed() {
    checkResult = CheckResult.OK;
  }

  private static ActiveMQConnectionFactory createConnectionFactory(ZipkinActiveMQConfig config) {
    ActiveMQConnectionFactory connectionFactory;
    if (StringUtil.isNotEmpty(config.getUsername())) {
      connectionFactory = new ActiveMQConnectionFactory(config.getUsername(), config.getPassword(), config.getUrl());
    } else {
      connectionFactory = new ActiveMQConnectionFactory(config.getUrl());
    }
    connectionFactory.setClientIDPrefix(config.getClientIdPrefix());
    connectionFactory.setConnectionIDPrefix(config.getConnectionIdPrefix());
    return connectionFactory;
  }
}
