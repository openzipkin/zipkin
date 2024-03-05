/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.context.annotation.Import;

/**
 * This disables automatic security configuration, deferring to {@linkplain EurekaSecurity}.
 * Doing so allows Eureka to start as if spring-security wasn't in the classpath.
 */
@SpringBootApplication(
  exclude = {SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class}
)
@EnableEurekaServer
@Import(EurekaSecurity.class)
public class EurekaServer {

  public static void main(String[] args) {
    SpringApplication.run(EurekaServer.class, args);
  }
}
