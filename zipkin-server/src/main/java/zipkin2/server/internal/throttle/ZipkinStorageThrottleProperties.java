/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal.throttle;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("zipkin.storage.throttle")
public final class ZipkinStorageThrottleProperties {
  /** Should we throttle at all? */
  private boolean enabled;
  /** Minimum number of storage requests to allow through at a given time. */
  private int minConcurrency;
  /**
   * Maximum number of storage requests to allow through at a given time. Should be tuned to
   * (bulk_index_pool_size / num_servers_in_cluster). e.g. 200 (default pool size in Elasticsearch)
   * / 2 (number of load balanced zipkin-server instances) = 100.
   */
  private int maxConcurrency;
  /**
   * Maximum number of storage requests to buffer while waiting for open Thread. 0 = no buffering.
   */
  private int maxQueueSize;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public int getMinConcurrency() {
    return minConcurrency;
  }

  public void setMinConcurrency(int minConcurrency) {
    this.minConcurrency = minConcurrency;
  }

  public int getMaxConcurrency() {
    return maxConcurrency;
  }

  public void setMaxConcurrency(int maxConcurrency) {
    this.maxConcurrency = maxConcurrency;
  }

  public int getMaxQueueSize() {
    return maxQueueSize;
  }

  public void setMaxQueueSize(int maxQueueSize) {
    this.maxQueueSize = maxQueueSize;
  }
}
