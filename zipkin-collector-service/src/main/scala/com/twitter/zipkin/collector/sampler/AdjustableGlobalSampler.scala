/*
 * Copyright 2012 Twitter Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.collector.sampler

import com.twitter.zipkin.config.sampler.AdjustableRateConfig

/**
 * Get the rate of sample from an adjustable source.
 */
class AdjustableGlobalSampler(sampleRateConfig: AdjustableRateConfig) extends GlobalSampler {

  /**
   * True: process trace
   * False: drop trace on the floor
   */
  override def apply(traceId: Long) : Boolean = {
    if (sample(traceId, sampleRateConfig.get)) {
      SAMPLER_PASSED.incr
      true
    } else {
      SAMPLER_DROPPED.incr
      false
    }
  }

  /**
   * NOTE:
   * We special case sampleRate = 1 since:
   *  - using the comparison "<" will not sample traceId = Long.MaxValue
   *    if sampleRate = 1
   *  - using the comparison "<=" will sample traceId = 0 if sampleRate = 0
   *
   * In addition, math.abs(Long.MinValue) = Long.MinValue due to overflow,
   * so we treat Long.MinValue as Long.MaxValue
   */
  def sample(traceId: Long, sampleRate: Double) : Boolean = {
    if (sampleRate == 1) {
      true
    } else {
      val t =
        if (traceId == Long.MinValue) Long.MaxValue
        else                          math.abs(traceId)
      t < Long.MaxValue * sampleRate
    }
  }

}
