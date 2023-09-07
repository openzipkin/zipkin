/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package zipkin.server.core;

import org.apache.skywalking.oap.server.library.module.ModuleConfig;

public class CoreModuleConfig extends ModuleConfig {
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

  public int getServiceNameMaxLength() {
    return serviceNameMaxLength;
  }

  public int getInstanceNameMaxLength() {
    return instanceNameMaxLength;
  }

  public int getEndpointNameMaxLength() {
    return endpointNameMaxLength;
  }

  public long getL1FlushPeriod() {
    return l1FlushPeriod;
  }

  public long getStorageSessionTimeout() {
    return storageSessionTimeout;
  }

  public int getServiceCacheRefreshInterval() {
    return serviceCacheRefreshInterval;
  }

  public int getMetricsDataTTL() {
    return metricsDataTTL;
  }

  public int getRecordDataTTL() {
    return recordDataTTL;
  }

  public org.apache.skywalking.oap.server.core.CoreModuleConfig toSkyWalkingConfig() {
    final org.apache.skywalking.oap.server.core.CoreModuleConfig result = new org.apache.skywalking.oap.server.core.CoreModuleConfig();
    result.setServiceCacheRefreshInterval(serviceCacheRefreshInterval);
    return result;
  }
}
