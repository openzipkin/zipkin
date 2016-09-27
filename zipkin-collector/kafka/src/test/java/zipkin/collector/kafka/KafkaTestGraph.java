/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.collector.kafka;

import java.util.Properties;
import kafka.common.FailedToSendMessageException;
import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import org.I0Itec.zkclient.ZkClient;
import org.I0Itec.zkclient.exception.ZkTimeoutException;
import org.junit.AssumptionViolatedException;

/** Tests only execute when ZK and Kafka are listening on 127.0.0.1 on default ports. */
enum KafkaTestGraph {
  INSTANCE;

  private AssumptionViolatedException ex;
  private Producer<String, byte[]> producer;

  synchronized Producer<String, byte[]> producer() {
    if (ex != null) throw ex;
    if (this.producer == null) {
      Properties producerProps = new Properties();
      producerProps.put("metadata.broker.list", "127.0.0.1:9092");
      producerProps.put("producer.type", "sync");
      producer = new Producer<>(new ProducerConfig(producerProps));
      try {
        new ZkClient("127.0.0.1:2181", 1000);
        producer.send(new KeyedMessage<>("test", new byte[0]));
      } catch (FailedToSendMessageException | ZkTimeoutException e) {
        throw ex = new AssumptionViolatedException(e.getMessage(), e);
      }
    }
    return producer;
  }
}
