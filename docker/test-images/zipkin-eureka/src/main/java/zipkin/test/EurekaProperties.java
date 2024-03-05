/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin.test;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Properties for configuring and building a {@link EurekaServer}. */
@ConfigurationProperties("eureka")
class EurekaProperties {

  /** Optional username to require. */
  private String username;

  /** Optional password to require. */
  private String password;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
