package com.twitter.zipkin.collector.sampler

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

import com.google.common.util.concurrent.Atomics
import org.scalactic.Tolerance
import org.scalatest.{FunSuite, Matchers}
import java.util.Random

class AdjustableGlobalSamplerSpec extends FunSuite with Matchers with Tolerance {

  /**
   * Zipkin trace ids are random 64bit numbers. This creates a relatively large
   * input to avoid flaking out due to PRNG nuance.
   */
  val traceIds = new Random().longs(100000).toArray

  /** Math.abs(Long.MinValue) returns a negative, coerse to Long.MaxValue */
  test("most negative number defence") {
    val sampler = new AdjustableGlobalSampler(Atomics.newReference(0.1f))
    sampler(Long.MinValue) should be (sampler(Long.MaxValue))
  }

  test("keep 10% of traces") {
    val sampleRate = 0.1f
    val sampler = new AdjustableGlobalSampler(Atomics.newReference(sampleRate))

    val passCount = traceIds.filter(sampler(_)).size

    // we expect results to be +- 3% of the target rate
    val expected = (traceIds.size * sampleRate).toInt
    val error = (expected * .03).toInt

    passCount should be (expected +- error)
  }

  /**
    * The collector needs to apply the same decision to incremental updates in a trace.
    */
  test("sample decisions are consistent for the same trace ids") {
    val sampler1 = new AdjustableGlobalSampler(Atomics.newReference(0.1f))
    val sampler2 = new AdjustableGlobalSampler(Atomics.newReference(0.1f))

    traceIds.filter(sampler1(_)).size should be(traceIds.filter(sampler2(_)).size)
  }

  test("drop all traces") {
    val sampler = new AdjustableGlobalSampler(Atomics.newReference(0f))

    sampler(Long.MinValue) should be (false)

    traceIds.filter(sampler(_)) should be(empty)

    sampler(Long.MaxValue) should be (false)
  }

  test("keep all traces") {
    val sampler = new AdjustableGlobalSampler(Atomics.newReference(1f))

    sampler(Long.MinValue) should be (true)

    traceIds.filter(sampler(_)) should be(traceIds)

    sampler(Long.MinValue) should be (true)
  }
}
