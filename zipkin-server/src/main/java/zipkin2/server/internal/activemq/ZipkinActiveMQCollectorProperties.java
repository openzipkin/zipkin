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
package zipkin2.server.internal.activemq;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin2.collector.activemq.ActiveMQCollector;

/** Properties for configuring and building a {@link ActiveMQCollector}. */
@ConfigurationProperties("zipkin.collector.activemq")
class ZipkinActiveMQCollectorProperties {
  /** URL of the ActiveMQ broker. */
  private String url;

  /** ActiveMQ queue from which to collect the Zipkin spans */
  private String queue;

  /** Client ID prefix for queue consumers */
  private String clientIdPrefix = "zipkin";

  /** Connection ID prefix for queue consumers */
  private String connectionIdPrefix = "zipkin";

  /** Number of concurrent span consumers */
  private Integer concurrency;

  /** Login user of the broker. */
  private String username;

  /** Login password of the broker. */
  private String password;

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = emptyToNull(url);
  }

  public String getQueue() {
    return queue;
  }

  public void setQueue(String queue) {
    this.queue = emptyToNull(queue);
  }

  public String getClientIdPrefix() {
    return clientIdPrefix;
  }

  public void setClientIdPrefix(String clientIdPrefix) {
    this.clientIdPrefix = clientIdPrefix;
  }

  public String getConnectionIdPrefix() {
    return connectionIdPrefix;
  }

  public void setConnectionIdPrefix(String connectionIdPrefix) {
    this.connectionIdPrefix = connectionIdPrefix;
  }

  public Integer getConcurrency() {
    return concurrency;
  }

  public void setConcurrency(Integer concurrency) {
    this.concurrency = concurrency;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = emptyToNull(username);
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = emptyToNull(password);
  }

  public ActiveMQCollector.Builder toBuilder() {
    final ActiveMQCollector.Builder result = ActiveMQCollector.builder();
    if (concurrency != null) result.concurrency(concurrency);
    if (queue != null) result.queue(queue);

    ActiveMQConnectionFactory connectionFactory;
    if (username != null) {
      connectionFactory = new ActiveMQConnectionFactory(username, password, url);
    } else {
      connectionFactory = new ActiveMQConnectionFactory(url);
    }
    connectionFactory.setClientIDPrefix(clientIdPrefix);
    connectionFactory.setConnectionIDPrefix(connectionIdPrefix);
    result.connectionFactory(connectionFactory);
    return result;
  }

  private static String emptyToNull(String s) {
    return "".equals(s) ? null : s;
  }
}
