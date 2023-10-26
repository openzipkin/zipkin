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
  private String gRPCHost;
  private int gRPCPort;
  private boolean gRPCSslEnabled = false;
  private String gRPCSslKeyPath;
  private String gRPCSslCertChainPath;
  private String gRPCSslTrustedCAPath;
  private int gRPCThreadPoolSize;
  private int gRPCThreadPoolQueueSize;
  private int gRPCMaxConcurrentCallsPerConnection;
  private int gRPCMaxMessageSize;

  private String restHost;
  private int restPort;
  private String restContextPath;
  private int restMaxThreads = 200;
  private long restIdleTimeOut = 30000;
  private int restAcceptQueueSize = 0;
  /**
   * The maximum size in bytes allowed for request headers.
   * Use -1 to disable it.
   */
  private int restMaxRequestHeaderSize = 8192;
  
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

  public String getGRPCHost() {
    return gRPCHost;
  }

  public void setGRPCHost(String gRPCHost) {
    this.gRPCHost = gRPCHost;
  }

  public int getGRPCPort() {
    return gRPCPort;
  }

  public void setGRPCPort(int gRPCPort) {
    this.gRPCPort = gRPCPort;
  }

  public boolean getGRPCSslEnabled() {
    return gRPCSslEnabled;
  }

  public void setGRPCSslEnabled(boolean gRPCSslEnabled) {
    this.gRPCSslEnabled = gRPCSslEnabled;
  }

  public String getGRPCSslKeyPath() {
    return gRPCSslKeyPath;
  }

  public void setGRPCSslKeyPath(String gRPCSslKeyPath) {
    this.gRPCSslKeyPath = gRPCSslKeyPath;
  }

  public String getGRPCSslCertChainPath() {
    return gRPCSslCertChainPath;
  }

  public void setGRPCSslCertChainPath(String gRPCSslCertChainPath) {
    this.gRPCSslCertChainPath = gRPCSslCertChainPath;
  }

  public String getGRPCSslTrustedCAPath() {
    return gRPCSslTrustedCAPath;
  }

  public void setGRPCSslTrustedCAPath(String gRPCSslTrustedCAPath) {
    this.gRPCSslTrustedCAPath = gRPCSslTrustedCAPath;
  }

  public int getGRPCThreadPoolSize() {
    return gRPCThreadPoolSize;
  }

  public void setGRPCThreadPoolSize(int gRPCThreadPoolSize) {
    this.gRPCThreadPoolSize = gRPCThreadPoolSize;
  }

  public int getGRPCThreadPoolQueueSize() {
    return gRPCThreadPoolQueueSize;
  }

  public void setGRPCThreadPoolQueueSize(int gRPCThreadPoolQueueSize) {
    this.gRPCThreadPoolQueueSize = gRPCThreadPoolQueueSize;
  }

  public int getGRPCMaxConcurrentCallsPerConnection() {
    return gRPCMaxConcurrentCallsPerConnection;
  }

  public void setGRPCMaxConcurrentCallsPerConnection(int gRPCMaxConcurrentCallsPerConnection) {
    this.gRPCMaxConcurrentCallsPerConnection = gRPCMaxConcurrentCallsPerConnection;
  }

  public int getGRPCMaxMessageSize() {
    return gRPCMaxMessageSize;
  }

  public void setGRPCMaxMessageSize(int gRPCMaxMessageSize) {
    this.gRPCMaxMessageSize = gRPCMaxMessageSize;
  }

  public String getRestHost() {
    return restHost;
  }

  public void setRestHost(String restHost) {
    this.restHost = restHost;
  }

  public int getRestPort() {
    return restPort;
  }

  public void setRestPort(int restPort) {
    this.restPort = restPort;
  }

  public String getRestContextPath() {
    return restContextPath;
  }

  public void setRestContextPath(String restContextPath) {
    this.restContextPath = restContextPath;
  }

  public int getRestMaxThreads() {
    return restMaxThreads;
  }

  public void setRestMaxThreads(int restMaxThreads) {
    this.restMaxThreads = restMaxThreads;
  }

  public long getRestIdleTimeOut() {
    return restIdleTimeOut;
  }

  public void setRestIdleTimeOut(long restIdleTimeOut) {
    this.restIdleTimeOut = restIdleTimeOut;
  }

  public int getRestAcceptQueueSize() {
    return restAcceptQueueSize;
  }

  public void setRestAcceptQueueSize(int restAcceptQueueSize) {
    this.restAcceptQueueSize = restAcceptQueueSize;
  }

  public int getRestMaxRequestHeaderSize() {
    return restMaxRequestHeaderSize;
  }

  public void setRestMaxRequestHeaderSize(int restMaxRequestHeaderSize) {
    this.restMaxRequestHeaderSize = restMaxRequestHeaderSize;
  }

  public boolean getSearchEnable() {
    return searchEnable;
  }

  public void setSearchEnable(boolean searchEnable) {
    this.searchEnable = searchEnable;
  }
}
