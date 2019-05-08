package zipkin2.server.internal

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import zipkin2.collector.CollectorMetrics
import zipkin2.collector.CollectorSampler
import zipkin2.storage.InMemoryStorage
import zipkin2.storage.StorageComponent

@Configuration
open class InMemoryCollectorConfiguration {
  @Bean open fun sampler(): CollectorSampler {
    return CollectorSampler.ALWAYS_SAMPLE
  }

  @Bean open fun metrics(): CollectorMetrics {
    return CollectorMetrics.NOOP_METRICS
  }

  @Bean open fun storage(): StorageComponent {
    return InMemoryStorage.newBuilder().build()
  }
}
