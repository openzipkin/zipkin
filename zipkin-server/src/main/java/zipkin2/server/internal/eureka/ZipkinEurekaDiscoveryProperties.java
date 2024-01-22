/*
 * Copyright 2015-2024 The OpenZipkin Authors
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
package zipkin2.server.internal.eureka;

import com.linecorp.armeria.server.eureka.EurekaUpdatingListener;
import com.linecorp.armeria.server.eureka.EurekaUpdatingListenerBuilder;
import java.io.Serializable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for Eureka registration.
 * <pre>{@code
 * zipkin.discovery.eureka:
 *   service-url: http://eureka:8761/eureka/v2
 *   appName: zipkin
 *   instance-id: zipkin-prod:zipkin:9411
 *   hostname: zipkin-prod
 * }</pre>
 */
@ConfigurationProperties("zipkin.discovery.eureka")
class ZipkinEurekaDiscoveryProperties implements Serializable { // for Spark jobs
  /** URL of the Eureka v2 endpoint. e.g. http://eureka:8761/eureka/v2 */
  private String serviceUrl;
  /** The appName property of this instance of zipkin. */
  private String appName;
  /** The instanceId property of this instance of zipkin. */
  private String instanceId;
  /** The hostName property of this instance of zipkin. */
  private String hostname;

  public String getServiceUrl() {
    return serviceUrl;
  }

  public void setServiceUrl(String serviceUrl) {
    this.serviceUrl = emptyToNull(serviceUrl);
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = emptyToNull(appName);
  }

  public String getInstanceId() {
    return instanceId;
  }

  public void setInstanceId(String instanceId) {
    this.instanceId = emptyToNull(instanceId);
  }

  public String getHostname() {
    return hostname;
  }

  public void setHostname(String hostname) {
    this.hostname = emptyToNull(hostname);
  }

  EurekaUpdatingListenerBuilder toBuilder() {
    final EurekaUpdatingListenerBuilder result = EurekaUpdatingListener.builder(serviceUrl);
    if (appName != null) result.appName(appName);
    if (instanceId != null) result.instanceId(instanceId);
    if (hostname != null) result.hostname(hostname);
    return result;
  }

  private static String emptyToNull(String s) {
    return "".equals(s) ? null : s;
  }
}
