/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.pulsar;

import org.springframework.boot.context.properties.ConfigurationProperties;
import zipkin2.collector.pulsar.PulsarCollector;

import java.util.LinkedHashMap;
import java.util.Map;

/** Properties for configuring and building a {@link PulsarCollector}. */
@ConfigurationProperties("zipkin.collector.pulsar")
class ZipkinPulsarCollectorProperties {

  /** The service URL for the Pulsar service. */
  private String serviceUrl;
  /** Pulsar topic span data will be retrieved from. */
  private String topic;
  /** Specify the subscription name for this consumer. */
  private String subscriptionName;
  /** Number of concurrent span consumers */
  private Integer concurrency;
  /** Additional Pulsar client configuration. */
  private Map<String, Object> clientProps = new LinkedHashMap<>();
  /** Additional Pulsar consumer configuration. */
  private Map<String, Object> consumerProps = new LinkedHashMap<>();

  public String getServiceUrl() {
    return serviceUrl;
  }

  public void setServiceUrl(String serviceUrl) {
    this.serviceUrl = serviceUrl;
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public String getSubscriptionName() {
    return subscriptionName;
  }

  public void setSubscriptionName(String subscriptionName) {
    this.subscriptionName = subscriptionName;
  }

  public Integer getConcurrency() {
    return concurrency;
  }

  public void setConcurrency(Integer concurrency) {
    this.concurrency = concurrency;
  }

  public Map<String, Object> getClientProps() {
    return clientProps;
  }

  public void setClientProps(Map<String, Object> clientProps) {
    this.clientProps = clientProps;
  }

  public Map<String, Object> getConsumerProps() {
    return consumerProps;
  }

  public void setConsumerProps(Map<String, Object> consumerProps) {
    this.consumerProps = consumerProps;
  }

  public PulsarCollector.Builder toBuilder() {
    final PulsarCollector.Builder result = PulsarCollector.builder();
    if (serviceUrl != null) {
      result.serviceUrl(serviceUrl);
    }
    if (topic != null) result.topic(topic);
    if (concurrency != null) result.concurrency(concurrency);
    if (subscriptionName != null) result.subscriptionName(subscriptionName);
    if (!clientProps.isEmpty()) result.clientProps(clientProps);
    if (!consumerProps.isEmpty()) result.consumerProps(consumerProps);
    return result;
  }
}
