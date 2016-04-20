/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package com.twitter.zipkin.sampler;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shows that {@link AdaptiveSampleRate} can be used in Java 7+.
 */
public final class AdaptiveSamplerInJava implements AutoCloseable {

  final AtomicLong boundary;
  final AtomicInteger spanCount;
  final AdaptiveSampleRate sampleRate;

  AdaptiveSamplerInJava() {
    this.boundary = new AtomicLong(Long.MAX_VALUE);
    this.spanCount = new AtomicInteger();
    this.sampleRate = new AdaptiveSampleRate(
        boundary,
        spanCount,
        "localhost:2181",
        Collections.emptyMap(),
        "/zipkin/sampler/adaptive",
        30,
        30 * 60,
        10 * 60,
        5 * 60
    );
  }

  @Override
  public void close() {
    sampleRate.close();
  }
}
