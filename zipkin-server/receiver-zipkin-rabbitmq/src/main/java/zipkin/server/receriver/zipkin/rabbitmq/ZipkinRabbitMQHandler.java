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

package zipkin.server.receriver.zipkin.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.receiver.zipkin.trace.SpanForward;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import zipkin2.Span;
import zipkin2.SpanBytesDecoderDetector;
import zipkin2.codec.BytesDecoder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class ZipkinRabbitMQHandler {
  private final ZipkinRabbitMQConfig config;
  private final SpanForward spanForward;

  private final ConnectionFactory connectionFactory = new ConnectionFactory();

  private final HistogramMetrics histogram;

  public ZipkinRabbitMQHandler(ZipkinRabbitMQConfig config, SpanForward spanForward, ModuleManager moduleManager) throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
    this.config = config;
    this.spanForward = spanForward;

    if (config.getConnectionTimeout() != null) connectionFactory.setConnectionTimeout(config.getConnectionTimeout());

    if (StringUtil.isNotEmpty(config.getUri())) {
      connectionFactory.setUri(URI.create(config.getUri()));
    } else {
      if (config.getPassword() != null) connectionFactory.setPassword(config.getPassword());
      if (config.getUsername() != null) connectionFactory.setUsername(config.getUsername());
      if (config.getVirtualHost() != null) connectionFactory.setVirtualHost(config.getVirtualHost());
      if (config.getUseSsl() != null && config.getUseSsl()) connectionFactory.useSslProtocol();
    }

    MetricsCreator metricsCreator = moduleManager.find(TelemetryModule.NAME)
        .provider()
        .getService(MetricsCreator.class);
    histogram = metricsCreator.createHistogramMetric(
        "trace_in_latency",
        "The process latency of trace data",
        new MetricsTag.Keys("protocol"),
        new MetricsTag.Values("zipkin-kafka")
    );
  }

  public void start() {
    Connection connection;
    try {
      connection =
          (CollectionUtils.isEmpty(config.getAddresses()))
              ? connectionFactory.newConnection()
              : connectionFactory.newConnection(convertAddresses(config.getAddresses()));
      declareQueueIfMissing(connection);
    } catch (IOException e) {
      throw new UncheckedIOException(
          "Unable to establish connection to RabbitMQ server: " + e.getMessage(), e);
    } catch (TimeoutException e) {
      throw new RuntimeException(
          "Timeout establishing connection to RabbitMQ server: " + e.getMessage(), e);
    }
    for (int i = 0; i < config.getConcurrency(); i++) {
      String consumerTag = "zipkin-rabbitmq." + i;
      try {
        // this sets up a channel for each consumer thread.
        // We don't track channels, as the connection will close its channels implicitly
        Channel channel = connection.createChannel();
        RabbitMQSpanConsumer consumer = new RabbitMQSpanConsumer(channel);
        channel.basicConsume(config.getQueue(), true, consumerTag, consumer);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to start RabbitMQ consumer " + consumerTag, e);
      }
    }
  }

  private void declareQueueIfMissing(Connection connection) throws IOException, TimeoutException {
    Channel channel = connection.createChannel();
    try {
      // check if queue already exists
      channel.queueDeclarePassive(config.getQueue());
      channel.close();
    } catch (IOException maybeQueueDoesNotExist) {
      Throwable cause = maybeQueueDoesNotExist.getCause();
      if (cause != null && cause.getMessage().contains("NOT_FOUND")) {
        channel = connection.createChannel();
        channel.queueDeclare(config.getQueue(), true, false, false, null);
        channel.close();
      } else {
        throw maybeQueueDoesNotExist;
      }
    }
  }

  /**
   * Consumes spans from messages on a RabbitMQ queue. Malformed messages will be discarded. Errors
   * in the storage component will similarly be ignored, with no retry of the message.
   */
  class RabbitMQSpanConsumer extends DefaultConsumer {
    RabbitMQSpanConsumer(Channel channel) {
      super(channel);
    }

    @Override
    public void handleDelivery(String tag, Envelope envelope, AMQP.BasicProperties props, byte[] body) {
      if (body.length == 0) return; // lenient on empty messages

      try (HistogramMetrics.Timer ignored = histogram.createTimer()) {
        BytesDecoder<Span> decoder;
        List<Span> out = new ArrayList<>();
        try {
          decoder = SpanBytesDecoderDetector.decoderForListMessage(body);
          decoder.decodeList(body, out);
        } catch (RuntimeException | Error e) {
          return;
        }
        spanForward.send(out);
      }
    }
  }

  static Address[] convertAddresses(List<String> addresses) {
    Address[] addressArray = new Address[addresses.size()];
    for (int i = 0; i < addresses.size(); i++) {
      String[] splitAddress = addresses.get(i).split(":", 100);
      String host = splitAddress[0];
      int port = -1;
      try {
        if (splitAddress.length == 2) port = Integer.parseInt(splitAddress[1]);
      } catch (NumberFormatException ignore) {
        // EmptyCatch ignored
      }
      addressArray[i] = (port > 0) ? new Address(host, port) : new Address(host);
    }
    return addressArray;
  }
}
