/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin.test;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import static java.nio.charset.StandardCharsets.UTF_8;

/** This enables security, particularly only BASIC auth, when {@code EUREKA_USERNAME} is set. */
@Configuration
@ConditionalOnProperty("eureka.username")
@EnableConfigurationProperties(EurekaProperties.class)
public class EurekaSecurity {
  @Bean FilterRegistrationBean<BasicAuthFilter> authFilter(EurekaProperties props) {
    FilterRegistrationBean<BasicAuthFilter> registrationBean = new FilterRegistrationBean<>();
    registrationBean.setFilter(new BasicAuthFilter(props.getUsername(), props.getPassword()));
    registrationBean.addUrlPatterns("/eureka/*"); // Auth /eureka, though only v2 is valid
    registrationBean.setOrder(2);
    return registrationBean;
  }

  /** Implements BASIC instead of spring-security + CORS, CSRF and management exclusions. */
  static final class BasicAuthFilter extends OncePerRequestFilter {
    final String expectedAuthorization;

    BasicAuthFilter(String username, String password) {
      expectedAuthorization =
        "Basic " + Base64.getEncoder().encodeToString((username + ':' + password).getBytes(UTF_8));
    }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
      FilterChain chain) throws ServletException, IOException {
      String authHeader = req.getHeader("Authorization");
      if (expectedAuthorization.equals(authHeader)) {
        chain.doFilter(req, res); // Pass on the supplied credentials
        return;
      }
      res.setHeader("WWW-Authenticate", "Basic realm=\"Realm'\"");
      res.sendError(HttpServletResponse.SC_UNAUTHORIZED); // Return 401 otherwise.
    }
  }
}
