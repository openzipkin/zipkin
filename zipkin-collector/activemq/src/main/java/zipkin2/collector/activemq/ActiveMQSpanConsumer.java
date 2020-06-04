/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.collector.activemq;

import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.QueueReceiver;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.transport.TransportListener;
import zipkin2.Callback;
import zipkin2.CheckResult;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Consumes spans from messages on a ActiveMQ queue. Malformed messages will be discarded. Errors in
 * the storage component will similarly be ignored, with no retry of the message.
 */
final class ActiveMQSpanConsumer implements TransportListener, MessageListener, Closeable {
  static final Callback<Void> NOOP = new Callback<Void>() {
    @Override public void onSuccess(Void value) {
    }

    @Override public void onError(Throwable t) {
    }
  };

  static final CheckResult
    CLOSED = CheckResult.failed(new IllegalStateException("Collector intentionally closed")),
    INTERRUPTION = CheckResult.failed(new IOException("Recoverable error on ActiveMQ connection"));

  final Collector collector;
  final CollectorMetrics metrics;

  final ActiveMQConnection connection;
  final Map<QueueSession, QueueReceiver> sessionToReceiver = new LinkedHashMap<>();

  volatile CheckResult checkResult = CheckResult.OK;

  ActiveMQSpanConsumer(Collector collector, CollectorMetrics metrics, ActiveMQConnection conn) {
    this.collector = collector;
    this.metrics = metrics;
    this.connection = conn;
    connection.addTransportListener(this);
  }

  /** JMS contract is one session per thread: we need a new session up to our concurrency level. */
  void registerInNewSession(ActiveMQConnection connection, String queue) throws JMSException {
    // Pass redundant info as we can't use default method in activeMQ
    QueueSession session = connection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
    // No need to do anything on ActiveMQ side as physical queues are created on demand
    Queue destination = session.createQueue(queue);
    QueueReceiver receiver = session.createReceiver(destination);
    receiver.setMessageListener(this);
    sessionToReceiver.put(session, receiver);
  }

  @Override public void onCommand(Object o) {
  }

  @Override public void onException(IOException error) {
    checkResult = CheckResult.failed(error);
  }

  @Override public void transportInterupted() {
    checkResult = INTERRUPTION;
  }

  @Override public void transportResumed() {
    checkResult = CheckResult.OK;
  }

  @Override public void onMessage(Message message) {
    metrics.incrementMessages();
    byte[] serialized; // TODO: consider how to reuse buffers here
    try {
      if (message instanceof BytesMessage) {
        BytesMessage bytesMessage = (BytesMessage) message;
        serialized = new byte[(int) bytesMessage.getBodyLength()];
        bytesMessage.readBytes(serialized);
      } else if (message instanceof TextMessage) {
        String text = ((TextMessage) message).getText();
        serialized = text.getBytes(UTF_8);
      } else {
        metrics.incrementMessagesDropped();
        return;
      }
    } catch (Exception e) {
      metrics.incrementMessagesDropped();
      return;
    }

    metrics.incrementBytes(serialized.length);
    if (serialized.length == 0) return; // lenient on empty messages
    collector.acceptSpans(serialized, NOOP);
  }

  @Override public void close() {
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
}
