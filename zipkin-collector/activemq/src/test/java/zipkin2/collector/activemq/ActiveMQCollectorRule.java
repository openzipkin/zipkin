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
package zipkin2.collector.activemq;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.junit.AssumptionViolatedException;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import zipkin2.CheckResult;
import zipkin2.collector.InMemoryCollectorMetrics;
import zipkin2.storage.InMemoryStorage;

import javax.jms.*;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

class ActiveMQCollectorRule extends ExternalResource {
  static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQCollectorRule.class);

  final InMemoryStorage storage = InMemoryStorage.newBuilder().build();
  final InMemoryCollectorMetrics metrics = new InMemoryCollectorMetrics();

  final InMemoryCollectorMetrics activemqMetrics = metrics.forTransport("activemq");


  GenericContainer container;
  ActiveMQCollector collector;

  BrokerService broker;



  @Override
  protected void before() throws Throwable {

    BrokerService broker;
    broker = new BrokerService();
    broker.setUseJmx(false);
    broker.setBrokerName("MyBroker");
    broker.setPersistent(false);
    broker.addConnector("tcp://localhost:61616");
    broker.start();

    Thread.sleep(10000);


    try {
      this.collector = tryToInitializeCollector();
    } catch (RuntimeException| Error e) {
      if (container == null) throw e;
      container.stop();
      container = null; // try with local connection instead
      this.collector = tryToInitializeCollector();
    }
  }

  ActiveMQCollector tryToInitializeCollector() {
    ActiveMQCollector result = computeCollectorBuilder().build();
    result.start();

    CheckResult check = result.check();
    if (!check.ok()) {
      throw new AssumptionViolatedException(check.error().getMessage(), check.error());
    }
    return result;
  }

  ActiveMQCollector.Builder computeCollectorBuilder() {
    return ActiveMQCollector.builder()
      .storage(storage)
      .metrics(metrics)
      .queue("zipkin")
      .addresses("tcp://localhost:61616");
  }


  void publish(byte[] message) throws IOException, TimeoutException {
    ConnectionFactory connectionFactory; //创建链接工厂
    Connection connection = null;//链接
    Session session;//创建会话
    Destination destination;//消息目的地 消息队列
    MessageProducer messageProducer;//消息生产者
    //实例化链接工厂  参数为 用户,密码,url
    connectionFactory = new ActiveMQConnectionFactory("", "", ActiveMQConnection.DEFAULT_BROKER_URL);
    try {
      connection=connectionFactory.createConnection();//通过链接工厂创建链接
      connection.start();//启动链接
      //创建会话 Session.AUTO_ACKNOWLEDGE。receive 或MessageListener.onMessage()成功返回的时候，自动确认收到消息。
      session =connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      //创建一个消息队列名称为hello ActiveMQ 消息队列中可包含需要发布消息
      destination = session.createQueue("zipkin");
      //将创建的消息队列hello ActiveMQ交给消息发布者messageProdecer
      messageProducer=session.createProducer(destination);

      BytesMessage bytesMessage = session.createBytesMessage();
      bytesMessage.writeBytes(message);
      messageProducer.send(bytesMessage);


    } catch (JMSException  e) {
      e.printStackTrace();
    }finally{
      try {
        //关闭连接
        connection.close();
      } catch (JMSException e) {
        e.printStackTrace();
      }
    }

  }


  @Override
  protected void after() {
    try {
      if (collector != null) collector.close();
    } catch (IOException e) {
      LOGGER.warn("error closing collector " + e.getMessage(), e);
    } finally {
      if (container != null) {
        container.stop();
      }
    }
  }
}
