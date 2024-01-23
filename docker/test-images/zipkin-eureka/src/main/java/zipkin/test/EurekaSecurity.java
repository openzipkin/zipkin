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
package zipkin.test;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder;

/** This enables security, particularly only BASIC auth, when {@code EUREKA_USERNAME} is set. */
@Configuration
@ConditionalOnProperty("eureka.username")
@EnableConfigurationProperties(EurekaProperties.class)
@Import(SecurityAutoConfiguration.class)
public class EurekaSecurity {
  @Bean InMemoryUserDetailsManager userDetailsService(EurekaProperties props) {
    PasswordEncoder encoder = createDelegatingPasswordEncoder();
    UserDetails user = User.withUsername(props.getUsername())
      .password(encoder.encode(props.getPassword()))
      .roles("ADMIN")
      .build();
    return new InMemoryUserDetailsManager(user);
  }

  /**
   * You have to disable CSRF to allow BASIC authenticating Eureka clients to operate.
   * <p>
   * See <a href="https://cloud.spring.io/spring-cloud-netflix/reference/html/#securing-the-eureka-server">Securing The Eureka Server</a>
   */
  @Bean SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.ignoringRequestMatchers("/actuator/health", "/eureka/**"));
    http.authorizeHttpRequests(authz -> authz.requestMatchers("/eureka/**").authenticated())
      .httpBasic(Customizer.withDefaults());
    return http.build();
  }
}
