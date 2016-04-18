/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.zipkin.sampler

import java.util.Random
import java.util.concurrent.atomic.AtomicLong

import org.scalatest.FunSuite

class SamplerTest extends FunSuite {
  val rnd = new Random(1L)

  test("is permissive when the boundary is max") {
    val sampler = new Sampler(new AtomicLong(Long.MaxValue))
    assert(sampler(Long.MaxValue))
    assert(sampler(Long.MinValue))
    assert(sampler(rnd.nextLong()))
  }

  test("is exclusive when the boundary is min") {
    val sampler = new Sampler(new AtomicLong(Long.MinValue))
    assert(!sampler(Long.MaxValue))
    assert(!sampler(Long.MinValue))
    assert(!sampler(rnd.nextLong()))
  }

  test("samples based on the given number") {
    val sampler = new Sampler(new AtomicLong((Long.MaxValue * 0.5f).toLong))
    assert(sampler((Long.MaxValue * 0.4f).toLong))
    assert(!sampler((Long.MaxValue * 0.6f).toLong))
  }

  test("will update based on the given new AtomicReference") {
    val v = new AtomicLong(Long.MinValue)
    val sampler = new Sampler(v)
    assert(!sampler(Long.MaxValue))

    v.set(Long.MaxValue)
    assert(sampler(Long.MaxValue))
  }
}
