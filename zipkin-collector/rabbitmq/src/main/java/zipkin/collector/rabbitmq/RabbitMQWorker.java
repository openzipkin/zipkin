/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.collector.rabbitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.io.IOException;

/**
 * A worker thread for concurrent processing of a RabbitMQ queue. This will idempotently declare the
 * queue from which it will consume. Auto-acknowledge is enabled so there will be no retrying of
 * messages.
 */
class RabbitMQWorker implements Runnable {
  private final Connection connection;
  private final RabbitMQCollector.Builder builder;
  private final String name;

  RabbitMQWorker(RabbitMQCollector.Builder builder, Connection connection, String name) {
    this.builder = builder;
    this.connection = connection;
    this.name = name;
  }

  @Override
  public void run() {
    try {
      Channel channel = this.connection.createChannel();
      channel.queueDeclare(this.builder.queue, true, false, false, null);
      final RabbitMQSpanConsumer consumer = new RabbitMQSpanConsumer(this.builder, channel);
      channel.basicConsume(this.builder.queue, true, this.name, consumer);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to start RabbitMQ consumer " + this.name, e);
    }
  }
}
