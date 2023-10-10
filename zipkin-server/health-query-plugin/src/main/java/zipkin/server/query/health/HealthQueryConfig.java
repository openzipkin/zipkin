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
package zipkin.server.query.health;

import org.apache.skywalking.oap.server.library.module.ModuleConfig;

public class HealthQueryConfig extends ModuleConfig {
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
}
