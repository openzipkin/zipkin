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
package zipkin2.server.internal.rabbitmq;

import com.rabbitmq.client.ConnectionFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin2.collector.rabbitmq.RabbitMQCollector;

/** Properties for configuring and building a {@link RabbitMQCollector}. */
@ConfigurationProperties("zipkin.collector.rabbitmq")
class ZipkinRabbitMQCollectorProperties {
  static final URI EMPTY_URI = URI.create("");

  /** RabbitMQ server addresses in the form of a (comma-separated) list of host:port pairs */
  private List<String> addresses;
  /** Number of concurrent consumers */
  private Integer concurrency = 1;
  /** TCP connection timeout in milliseconds */
  private Integer connectionTimeout;
  /** RabbitMQ user password */
  private String password;
  /** RabbitMQ queue from which to collect the Zipkin spans */
  private String queue;
  /** RabbitMQ username */
  private String username;
  /** RabbitMQ virtual host */
  private String virtualHost;
  /** Flag to use SSL */
  private Boolean useSsl;
  /**
   * RabbitMQ URI spec-compliant URI to connect to the RabbitMQ server. When used, other connection
   * properties will be ignored.
   */
  private URI uri;

  public List<String> getAddresses() {
    return addresses;
  }

  public void setAddresses(List<String> addresses) {
    this.addresses = addresses;
  }

  public int getConcurrency() {
    return concurrency;
  }

  public void setConcurrency(int concurrency) {
    this.concurrency = concurrency;
  }

  public Integer getConnectionTimeout() {
    return connectionTimeout;
  }

  public void setConnectionTimeout(Integer connectionTimeout) {
    this.connectionTimeout = connectionTimeout;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getQueue() {
    return queue;
  }

  public void setQueue(String queue) {
    this.queue = queue;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getVirtualHost() {
    return virtualHost;
  }

  public void setVirtualHost(String virtualHost) {
    this.virtualHost = virtualHost;
  }

  public Boolean getUseSsl() {
    return useSsl;
  }

  public void setUseSsl(Boolean useSsl) {
    this.useSsl = useSsl;
  }

  public URI getUri() {
    return uri;
  }

  public void setUri(URI uri) {
    if (EMPTY_URI.equals(uri)) return;
    this.uri = uri;
  }

  public RabbitMQCollector.Builder toBuilder()
      throws KeyManagementException, NoSuchAlgorithmException, URISyntaxException {
    final RabbitMQCollector.Builder result = RabbitMQCollector.builder();
    ConnectionFactory connectionFactory = new ConnectionFactory();
    if (concurrency != null) result.concurrency(concurrency);
    if (connectionTimeout != null) connectionFactory.setConnectionTimeout(connectionTimeout);
    if (queue != null) result.queue(queue);

    if (uri != null) {
      connectionFactory.setUri(uri);
    } else {
      if (addresses != null) result.addresses(addresses);
      if (password != null) connectionFactory.setPassword(password);
      if (username != null) connectionFactory.setUsername(username);
      if (virtualHost != null) connectionFactory.setVirtualHost(virtualHost);
      if (useSsl != null && useSsl) connectionFactory.useSslProtocol();
    }
    result.connectionFactory(connectionFactory);
    return result;
  }
}
