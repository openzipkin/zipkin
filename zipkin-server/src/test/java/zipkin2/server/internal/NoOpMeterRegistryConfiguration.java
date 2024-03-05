/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import com.linecorp.armeria.common.metric.NoopMeterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NoOpMeterRegistryConfiguration {
  @Bean public MeterRegistry noOpMeterRegistry() {
    return NoopMeterRegistry.get();
  }
}
