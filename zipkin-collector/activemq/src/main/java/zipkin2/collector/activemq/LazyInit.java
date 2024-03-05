/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.collector.activemq;

import javax.jms.JMSException;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;

import static zipkin2.collector.activemq.ActiveMQCollector.uncheckedException;

/**
 * Lazy creates a connection and registers a message listener up to the specified concurrency level.
 * This listener will also receive health notifications.
 */
final class LazyInit {
  final Collector collector;
  final CollectorMetrics metrics;
  final ActiveMQConnectionFactory connectionFactory;
  final String queue;
  final int concurrency;

  volatile ActiveMQSpanConsumer result;

  LazyInit(ActiveMQCollector.Builder builder) {
    collector = builder.delegate.build();
    metrics = builder.metrics;
    connectionFactory = builder.connectionFactory;
    queue = builder.queue;
    concurrency = builder.concurrency;
  }

  ActiveMQSpanConsumer init() {
    if (result == null) {
      synchronized (this) {
        if (result == null) {
          result = doInit();
        }
      }
    }
    return result;
  }

  void close() {
    ActiveMQSpanConsumer maybe = result;
    if (maybe != null) result.close();
  }

  ActiveMQSpanConsumer doInit() {
    final ActiveMQConnection connection;
    try {
      connection = (ActiveMQConnection) connectionFactory.createQueueConnection();
      connection.start();
    } catch (JMSException e) {
      throw uncheckedException("Unable to establish connection to ActiveMQ broker: ", e);
    }

    try {
      ActiveMQSpanConsumer result = new ActiveMQSpanConsumer(collector, metrics, connection);

      for (int i = 0; i < concurrency; i++) {
        result.registerInNewSession(connection, queue);
      }

      return result;
    } catch (JMSException e) {
      try {
        connection.close();
      } catch (JMSException ignored) {
        // EmptyCatch ignored
      }
      throw uncheckedException("Unable to create queueReceiver(" + queue + "): ", e);
    }
  }
}
