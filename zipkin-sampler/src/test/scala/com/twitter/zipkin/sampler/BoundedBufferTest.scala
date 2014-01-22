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

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BoundedBufferTest extends FunSuite {
  test("behaves as a bounded buffer by dropping old entries") {
    val buf = new BoundedBuffer[Int](3)

    buf.push(1)
    assert(buf.snap === Seq(1))

    buf.push(2)
    assert(buf.snap === Seq(2, 1))

    buf.push(3)
    assert(buf.snap === Seq(3, 2, 1))

    buf.push(4)
    assert(buf.snap === Seq(4, 3, 2))

    buf.push(5)
    assert(buf.snap === Seq(5, 4, 3))
  }

  test("provides access to the ends") {
    val buf = new BoundedBuffer[Int](3)

    buf.push(1)
    assert(buf.snapEnds === (1, 1))

    buf.push(2)
    assert(buf.snapEnds === (2, 1))

    buf.push(3)
    assert(buf.snapEnds === (3, 1))

    buf.push(4)
    assert(buf.snapEnds === (4, 2))
  }
}
