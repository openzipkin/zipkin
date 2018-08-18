package zipkin2.server.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import zipkin2.collector.ConcurrencyLimiter;

@Configuration
@EnableConfigurationProperties(ConcurrencyLimiterProperties.class)
@ConditionalOnProperty(name = "zipkin.collector.concurrency.enabled")
public class ConcurrencyLimiterConfiguration {

  @Bean
  public ConcurrencyLimiter limiter(ConcurrencyLimiterProperties limiter) {
    if(limiter != null) {
      return limiter.build();
    }
    return null;
  }

}
