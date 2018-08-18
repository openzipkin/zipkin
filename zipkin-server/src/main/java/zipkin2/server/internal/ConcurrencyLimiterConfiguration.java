/*
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.server.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import zipkin2.collector.ConcurrencyLimiter;

@Configuration
@EnableConfigurationProperties(ConcurrencyLimiterProperties.class)
@ConditionalOnProperty(name = "zipkin.collector.concurrency.enabled", havingValue = "true")
public class ConcurrencyLimiterConfiguration {

  @Bean
  @Lazy
  public ConcurrencyLimiter limiter(ConcurrencyLimiterProperties limiter) {
    if(limiter != null) {
      return limiter.build();
    }
    return null;
  }

}
