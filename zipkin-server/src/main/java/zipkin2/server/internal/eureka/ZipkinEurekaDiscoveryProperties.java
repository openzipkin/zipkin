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

import com.linecorp.armeria.common.auth.BasicToken;
import com.linecorp.armeria.server.eureka.EurekaUpdatingListener;
import com.linecorp.armeria.server.eureka.EurekaUpdatingListenerBuilder;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
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
  /**
   * URL of the Eureka v2 endpoint. e.g. http://eureka:8761/eureka/v2
   *
   * <p>Note: When present, {@link URI#getUserInfo() userInfo} credentials will be used for BASIC
   * authentication. For example, if the URL is "https://myuser:mypassword@1.1.3.1/eureka/v2/",
   * requests to Eureka will authenticate with the user "myuser" and password "mypassword".
   */
  private URI serviceUrl;
  /** The appName property of this instance of zipkin. */
  private String appName;
  /** The instanceId property of this instance of zipkin. */
  private String instanceId;
  /** The hostName property of this instance of zipkin. */
  private String hostname;

  private BasicToken auth;

  public URI getServiceUrl() {
    return serviceUrl;
  }

  public void setServiceUrl(URI serviceUrl) {
    if (serviceUrl == null || serviceUrl.toString().isEmpty()) {
      this.serviceUrl = null;
      return;
    }
    if (serviceUrl.getUserInfo() != null) {
      String[] ui = serviceUrl.getUserInfo().split(":");
      if (ui.length == 2) {
        auth = BasicToken.ofBasic(ui[0], ui[1]);
      }
    }
    this.serviceUrl = stripBaseUrl(serviceUrl);
  }

  // Strip the credentials and any invalid query or fragment from the URI:
  // The Eureka API doesn't define any global query params or fragment.
  // See https://github.com/Netflix/eureka/wiki/Eureka-REST-operations
  static URI stripBaseUrl(URI serviceUrl) {
    try {
      return new URI(serviceUrl.getScheme(), null, serviceUrl.getHost(), serviceUrl.getPort(),
        serviceUrl.getPath(), null, null);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e);
    }
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
    EurekaUpdatingListenerBuilder result = EurekaUpdatingListener.builder(serviceUrl)
      .healthCheckUrlPath("/health");
    if (auth != null) result.auth(auth);
    if (appName != null) result.appName(appName);
    if (instanceId != null) result.instanceId(instanceId);
    if (hostname != null) {
      result.hostname(hostname);
      // Armeria defaults the vipAddress incorrectly to include the port. This is redundant with
      // the server port, so override it until we have a PR to fix that.
      result.vipAddress(hostname);
    }
    return result;
  }

  private static String emptyToNull(String s) {
    return "".equals(s) ? null : s;
  }
}
