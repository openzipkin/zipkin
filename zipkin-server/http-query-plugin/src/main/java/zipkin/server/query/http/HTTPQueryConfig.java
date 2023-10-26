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

package zipkin.server.query.http;

import org.apache.skywalking.oap.query.zipkin.ZipkinQueryConfig;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

public class HTTPQueryConfig extends ModuleConfig {
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

  private boolean strictTraceId = true;
  private long lookback = 86400000L;
  private int namesMaxAge = 300;
  private int uiQueryLimit = 10;
  private String uiEnvironment = "";
  private long uiDefaultLookback = 900000L;
  private String allowedOrigins = "*";

  private boolean uiEnable = true;
  private String uiBasePath = "/zipkin";

  private boolean dependencyEnabled = true;
  private double dependencyLowErrorRate = 0.5;  // 50% of calls in error turns line yellow
  private double dependencyHighErrorRate = 0.75;// 75% of calls in error turns line red

  public ZipkinQueryConfig toSkyWalkingConfig(boolean searchEnabled) {
    final ZipkinQueryConfig result = new ZipkinQueryConfig();
    result.setLookback(lookback);
    result.setNamesMaxAge(namesMaxAge);
    result.setUiQueryLimit(uiQueryLimit);
    result.setUiEnvironment(uiEnvironment);
    result.setUiDefaultLookback(uiDefaultLookback);
    result.setUiSearchEnabled(searchEnabled);
    return result;
  }

  public long getLookback() {
    return lookback;
  }

  public void setLookback(long lookback) {
    this.lookback = lookback;
  }

  public int getNamesMaxAge() {
    return namesMaxAge;
  }

  public void setNamesMaxAge(int namesMaxAge) {
    this.namesMaxAge = namesMaxAge;
  }

  public int getUiQueryLimit() {
    return uiQueryLimit;
  }

  public void setUiQueryLimit(int uiQueryLimit) {
    this.uiQueryLimit = uiQueryLimit;
  }

  public String getUiEnvironment() {
    return uiEnvironment;
  }

  public void setUiEnvironment(String uiEnvironment) {
    this.uiEnvironment = uiEnvironment;
  }

  public long getUiDefaultLookback() {
    return uiDefaultLookback;
  }

  public void setUiDefaultLookback(long uiDefaultLookback) {
    this.uiDefaultLookback = uiDefaultLookback;
  }

  public boolean getStrictTraceId() {
    return strictTraceId;
  }

  public void setStrictTraceId(boolean strictTraceId) {
    this.strictTraceId = strictTraceId;
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

  public String getUiBasePath() {
    return uiBasePath;
  }

  public void setUiBasePath(String uiBasePath) {
    this.uiBasePath = uiBasePath;
  }

  public boolean getUiEnable() {
    return uiEnable;
  }

  public void setUiEnable(boolean uiEnable) {
    this.uiEnable = uiEnable;
  }

  public String getAllowedOrigins() {
    return allowedOrigins;
  }

  public void setAllowedOrigins(String allowedOrigins) {
    this.allowedOrigins = allowedOrigins;
  }

  public boolean getDependencyEnabled() {
    return dependencyEnabled;
  }

  public void setDependencyEnabled(boolean dependencyEnabled) {
    this.dependencyEnabled = dependencyEnabled;
  }

  public double getDependencyLowErrorRate() {
    return dependencyLowErrorRate;
  }

  public void setDependencyLowErrorRate(double dependencyLowErrorRate) {
    this.dependencyLowErrorRate = dependencyLowErrorRate;
  }

  public double getDependencyHighErrorRate() {
    return dependencyHighErrorRate;
  }

  public void setDependencyHighErrorRate(double dependencyHighErrorRate) {
    this.dependencyHighErrorRate = dependencyHighErrorRate;
  }
}
