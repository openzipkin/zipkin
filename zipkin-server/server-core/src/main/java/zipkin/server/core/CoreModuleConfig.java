/*
 * Copyright 2015-2023 The OpenZipkin Authors
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
package zipkin.server.core;

import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

public class CoreModuleConfig extends ModuleConfig {
  private String serverHost;
  private int serverPort;
  private int serverMaxThreads = 200;
  private long serverIdleTimeOut = 30000;
  private int serverAcceptQueueSize = 0;
  /**
   * The maximum size in bytes allowed for request headers.
   * Use -1 to disable it.
   */
  private int serverMaxRequestHeaderSize = 8192;
  private boolean serverEnableTLS = false;
  private String serverTLSKeyPath;
  private String serverTLSCertChainPath;
  private String clusterSslTrustedCAPath;

  /**
   * The max length of the service name.
   */
  private int serviceNameMaxLength = 70;
  /**
   * The max length of the service instance name.
   */
  private int instanceNameMaxLength = 70;
  /**
   * The max length of the endpoint name.
   *
   * <p>NOTICE</p>
   * In the current practice, we don't recommend the length over 190.
   */
  private int endpointNameMaxLength = 150;
  /**
   * The period of L1 aggregation flush. Unit is ms.
   */
  private long l1FlushPeriod = 500;
  /**
   * The threshold of session time. Unit is ms. Default value is 70s.
   */
  private long storageSessionTimeout = 70_000;
  /**
   * The service cache refresh interval, default 10s
   */
  private int serviceCacheRefreshInterval = 10;
  /**
   * The time to live of all metrics data. Unit is day.
   */
  private int metricsDataTTL = 3;
  /**
   * The time to live of all record data, including tracing. Unit is Day.
   */
  private int recordDataTTL = 7;

  /**
   * Defines a set of span tag keys which are searchable.
   * The max length of key=value should be less than 256 or will be dropped.
   */
  private String searchableTracesTags = DEFAULT_SEARCHABLE_TAG_KEYS;

  /**
   * The trace sample rate precision is 0.0001, should be between 0 and 1.
   */
  private double traceSampleRate = 1.0f;

  /**
   * The number of threads used to prepare metrics data to the storage.
   */
  private int prepareThreads = 2;

  /**
   * The period of doing data persistence. Unit is second.
   */
  private int persistentPeriod = 25;

  /**
   * Timeout for cluster internal communication, in seconds.
   */
  private int remoteTimeout = 20;

  private boolean searchEnable = true;

  private static final String DEFAULT_SEARCHABLE_TAG_KEYS = String.join(
      Const.COMMA,
      "http.method"
  );

  public org.apache.skywalking.oap.server.core.CoreModuleConfig toSkyWalkingConfig() {
    final org.apache.skywalking.oap.server.core.CoreModuleConfig result = new org.apache.skywalking.oap.server.core.CoreModuleConfig();
    result.setServiceCacheRefreshInterval(serviceCacheRefreshInterval);
    result.setPrepareThreads(prepareThreads);
    result.setPersistentPeriod(persistentPeriod);
    return result;
  }

  public int getServiceNameMaxLength() {
    return serviceNameMaxLength;
  }

  public void setServiceNameMaxLength(int serviceNameMaxLength) {
    this.serviceNameMaxLength = serviceNameMaxLength;
  }

  public int getInstanceNameMaxLength() {
    return instanceNameMaxLength;
  }

  public void setInstanceNameMaxLength(int instanceNameMaxLength) {
    this.instanceNameMaxLength = instanceNameMaxLength;
  }

  public int getEndpointNameMaxLength() {
    return endpointNameMaxLength;
  }

  public void setEndpointNameMaxLength(int endpointNameMaxLength) {
    this.endpointNameMaxLength = endpointNameMaxLength;
  }

  public long getL1FlushPeriod() {
    return l1FlushPeriod;
  }

  public void setL1FlushPeriod(long l1FlushPeriod) {
    this.l1FlushPeriod = l1FlushPeriod;
  }

  public long getStorageSessionTimeout() {
    return storageSessionTimeout;
  }

  public void setStorageSessionTimeout(long storageSessionTimeout) {
    this.storageSessionTimeout = storageSessionTimeout;
  }

  public int getServiceCacheRefreshInterval() {
    return serviceCacheRefreshInterval;
  }

  public void setServiceCacheRefreshInterval(int serviceCacheRefreshInterval) {
    this.serviceCacheRefreshInterval = serviceCacheRefreshInterval;
  }

  public int getMetricsDataTTL() {
    return metricsDataTTL;
  }

  public void setMetricsDataTTL(int metricsDataTTL) {
    this.metricsDataTTL = metricsDataTTL;
  }

  public int getRecordDataTTL() {
    return recordDataTTL;
  }

  public void setRecordDataTTL(int recordDataTTL) {
    this.recordDataTTL = recordDataTTL;
  }

  public String getSearchableTracesTags() {
    return searchableTracesTags;
  }

  public void setSearchableTracesTags(String searchableTracesTags) {
    this.searchableTracesTags = searchableTracesTags;
  }

  public double getTraceSampleRate() {
    return traceSampleRate;
  }

  public void setTraceSampleRate(double traceSampleRate) {
    this.traceSampleRate = traceSampleRate;
  }

  public int getPrepareThreads() {
    return prepareThreads;
  }

  public void setPrepareThreads(int prepareThreads) {
    this.prepareThreads = prepareThreads;
  }

  public int getPersistentPeriod() {
    return persistentPeriod;
  }

  public void setPersistentPeriod(int persistentPeriod) {
    this.persistentPeriod = persistentPeriod;
  }

  public int getRemoteTimeout() {
    return remoteTimeout;
  }

  public void setRemoteTimeout(int remoteTimeout) {
    this.remoteTimeout = remoteTimeout;
  }

  public boolean getSearchEnable() {
    return searchEnable;
  }

  public void setSearchEnable(boolean searchEnable) {
    this.searchEnable = searchEnable;
  }

  public String getServerHost() {
    return serverHost;
  }

  public void setServerHost(String serverHost) {
    this.serverHost = serverHost;
  }

  public int getServerPort() {
    return serverPort;
  }

  public void setServerPort(int serverPort) {
    this.serverPort = serverPort;
  }

  public int getServerMaxThreads() {
    return serverMaxThreads;
  }

  public void setServerMaxThreads(int serverMaxThreads) {
    this.serverMaxThreads = serverMaxThreads;
  }

  public long getServerIdleTimeOut() {
    return serverIdleTimeOut;
  }

  public void setServerIdleTimeOut(long serverIdleTimeOut) {
    this.serverIdleTimeOut = serverIdleTimeOut;
  }

  public int getServerAcceptQueueSize() {
    return serverAcceptQueueSize;
  }

  public void setServerAcceptQueueSize(int serverAcceptQueueSize) {
    this.serverAcceptQueueSize = serverAcceptQueueSize;
  }

  public int getServerMaxRequestHeaderSize() {
    return serverMaxRequestHeaderSize;
  }

  public void setServerMaxRequestHeaderSize(int serverMaxRequestHeaderSize) {
    this.serverMaxRequestHeaderSize = serverMaxRequestHeaderSize;
  }

  public boolean getServerEnableTLS() {
    return serverEnableTLS;
  }

  public void setServerEnableTLS(boolean serverEnableTLS) {
    this.serverEnableTLS = serverEnableTLS;
  }

  public String getServerTLSKeyPath() {
    return serverTLSKeyPath;
  }

  public void setServerTLSKeyPath(String serverTLSKeyPath) {
    this.serverTLSKeyPath = serverTLSKeyPath;
  }

  public String getServerTLSCertChainPath() {
    return serverTLSCertChainPath;
  }

  public void setServerTLSCertChainPath(String serverTLSCertChainPath) {
    this.serverTLSCertChainPath = serverTLSCertChainPath;
  }

  public String getClusterSslTrustedCAPath() {
    return clusterSslTrustedCAPath;
  }

  public void setClusterSslTrustedCAPath(String clusterSslTrustedCAPath) {
    this.clusterSslTrustedCAPath = clusterSslTrustedCAPath;
  }
}
