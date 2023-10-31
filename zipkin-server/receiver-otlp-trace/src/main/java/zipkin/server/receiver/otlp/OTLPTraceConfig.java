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

package zipkin.server.receiver.otlp;

import org.apache.skywalking.oap.server.library.module.ModuleConfig;

public class OTLPTraceConfig extends ModuleConfig {
  private String gRPCHost;
  /**
   * Only setting the real port(not 0) makes the gRPC server online.
   */
  private int gRPCPort;
  private int maxConcurrentCallsPerConnection;
  private int maxMessageSize;
  private int gRPCThreadPoolSize;
  private int gRPCThreadPoolQueueSize;
  private String authentication;
  private boolean gRPCSslEnabled = false;
  private String gRPCSslKeyPath;
  private String gRPCSslCertChainPath;
  private String gRPCSslTrustedCAsPath;

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

  public int getMaxConcurrentCallsPerConnection() {
    return maxConcurrentCallsPerConnection;
  }

  public void setMaxConcurrentCallsPerConnection(int maxConcurrentCallsPerConnection) {
    this.maxConcurrentCallsPerConnection = maxConcurrentCallsPerConnection;
  }

  public int getMaxMessageSize() {
    return maxMessageSize;
  }

  public void setMaxMessageSize(int maxMessageSize) {
    this.maxMessageSize = maxMessageSize;
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

  public String getAuthentication() {
    return authentication;
  }

  public void setAuthentication(String authentication) {
    this.authentication = authentication;
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

  public String getGRPCSslTrustedCAsPath() {
    return gRPCSslTrustedCAsPath;
  }

  public void setGRPCSslTrustedCAsPath(String gRPCSslTrustedCAsPath) {
    this.gRPCSslTrustedCAsPath = gRPCSslTrustedCAsPath;
  }
}
