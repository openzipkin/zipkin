/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.server.internal.brave;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties("zipkin.self-tracing")
class SelfTracingProperties {

  /** Whether self-tracing is enabled. Defaults to {@code false}. */
  private boolean enabled = false;
  /**
   * The percentage of traces retained when self-tracing. If 1.0 (i.e., all traces are sampled), the
   * value of {@link #getTracesPerSecond()} will be used for sampling traces. Defaults to {@code
   * 1.0}, sampling all traces.
   */
  private float sampleRate = 1.0f;
  /**
   * The number of traces per second to retain. If 0, an unlimited number of traces will be
   * retained. This value has no effect if {@link #getSampleRate()} is set to something other than
   * {@code 1.0}. Defaults to 1 trace per second.
   */
  private int tracesPerSecond = 1;
  /** Timeout to flush self-tracing data to storage. */
  @DurationUnit(ChronoUnit.SECONDS)
  private Duration messageTimeout = Duration.ofSeconds(1);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public float getSampleRate() {
    return sampleRate;
  }

  public void setSampleRate(float sampleRate) {
    this.sampleRate = sampleRate;
  }

  public int getTracesPerSecond() {
    return tracesPerSecond;
  }

  public void setTracesPerSecond(int tracesPerSecond) {
    this.tracesPerSecond = tracesPerSecond;
  }

  public Duration getMessageTimeout() {
    return messageTimeout;
  }

  public void setMessageTimeout(Duration messageTimeout) {
    this.messageTimeout = messageTimeout;
  }
}
