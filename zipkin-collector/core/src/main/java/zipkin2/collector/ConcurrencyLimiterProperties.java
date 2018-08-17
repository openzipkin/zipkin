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
package zipkin2.collector;

import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.FixedLimit;
import com.netflix.concurrency.limits.limiter.BlockingLimiter;
import com.netflix.concurrency.limits.limiter.DefaultLimiter;
import com.netflix.concurrency.limits.strategy.SimpleStrategy;

import java.util.concurrent.TimeUnit;

public class ConcurrencyLimiterProperties {

  private Integer concurrency = 1;

  private Boolean blocking = Boolean.TRUE;

  private Integer limit = 8;

  private Long maxWindowTime;

  private Long minWindowTime;

  private Integer windowSize;

  private Long minRttThreshold;

  public Integer getConcurrency() {
    return concurrency;
  }

  public void setConcurrency(Integer concurrency) {
    this.concurrency = concurrency;
  }

  public boolean isBlocking() {
    return blocking;
  }

  public void setBlocking(Boolean blocking) {
    this.blocking = blocking;
  }

  public Integer getLimit() {
    return limit;
  }

  public void setLimit(Integer limit) {
    this.limit = limit;
  }

  public Long getMaxWindowTime() {
    return maxWindowTime;
  }

  public void setMaxWindowTime(Long maxWindowTime) {
    this.maxWindowTime = maxWindowTime;
  }

  public Long getMinWindowTime() {
    return minWindowTime;
  }

  public void setMinWindowTime(Long minWindowTime) {
    this.minWindowTime = minWindowTime;
  }

  public Integer getWindowSize() {
    return windowSize;
  }

  public void setWindowSize(Integer windowSize) {
    this.windowSize = windowSize;
  }

  public Long getMinRttThreshold() {
    return minRttThreshold;
  }

  public void setMinRttThreshold(Long minRttThreshold) {
    this.minRttThreshold = minRttThreshold;
  }

  public ConcurrencyLimiter build() {

    ConcurrencyLimiter result = new ConcurrencyLimiter();
    if(concurrency != null) {
      result.setThreads(concurrency);
    }

    DefaultLimiter.Builder builder = DefaultLimiter.newBuilder();

    Limit theLimit = FixedLimit.of(limit);
    // TODO: add more limits... Vegas, Gradient, AIMD
    builder.limit(theLimit);

    if(maxWindowTime != null) {
      builder.maxWindowTime(maxWindowTime, TimeUnit.SECONDS);
    }
    if(minWindowTime != null) {
      builder.minWindowTime(minWindowTime, TimeUnit.SECONDS);
    }
    if(windowSize != null) {
      builder.windowSize(windowSize);
    }
    if(minRttThreshold != null) {
      builder.minRttThreshold(minRttThreshold, TimeUnit.SECONDS);
    }

    Limiter<Void> limiter = builder.build(new SimpleStrategy<>());

    if(blocking) {
      result.setLimiter(BlockingLimiter.wrap(limiter));
    } else {
      result.setLimiter(limiter);
    }

    return result;
  }

}
