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

import com.twitter.ostrich.stats.Stats

/**
 * Even after the traces have been created and transported to the
 * collector we want to have the option to sample before it hits storage.
 *
 * This gives us the opportunity to create more traces then we
 * can store and in the common case drop them. We can increase
 * the data let through if we temporarily need more to look at, when
 * troubleshooting for example.
 */
trait GlobalSampler {

  val SAMPLER_PASSED = Stats.getCounter("sampler_passed")
  val SAMPLER_DROPPED = Stats.getCounter("sampler_dropped")

  /**
   * True: drop trace on the floor
   * False: process trace
   */
  def apply(traceId: Long): Boolean = false

}

/**
 * None shall pass! Drop all the trace data.
 */
object NullGlobalSampler extends GlobalSampler {
  override def apply(traceId: Long) = false
}

/**
 * Let everything through.
 */
object EverythingGlobalSampler extends GlobalSampler {
  override def apply(traceId: Long) = true
}
