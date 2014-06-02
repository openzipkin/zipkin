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

import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.util.{MockTimer, Time, Var}
import com.twitter.conversions.time._
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RequestRateCheckTest extends FunSuite {
  test("fails when the request rate is non-positive") {
    val rate = Var(1)
    val check = new RequestRateCheck[Unit](rate)
    assert(check(Some(())).isDefined)

    rate.update(0)
    assert(check(Some(())).isEmpty)
  }
}

@RunWith(classOf[JUnitRunner])
class SuffcientDataCheckTest extends FunSuite {
  test("fails when sufficient data is not present") {
    val check = new SufficientDataCheck[Unit](2)
    assert(check(Some(Seq.empty[Unit])).isEmpty)
    assert(check(Some(Seq((), ()))).isDefined)
    assert(check(Some(Seq((), (), ()))).isDefined)
  }
}

@RunWith(classOf[JUnitRunner])
class ValidDataCheckTest extends FunSuite {
  test("fails when data fails validation") {
    val check = new ValidDataCheck[Int](_ > 1)
    assert(check(Some(Seq(0, 1, 2))).isEmpty)
    assert(check(Some(Seq(2, 3, 4))).isDefined)
  }
}

@RunWith(classOf[JUnitRunner])
class CooldownCheckTest extends FunSuite {
  test("allows only once per period") {
    Time.withCurrentTimeFrozen { tc =>
      val timer = new MockTimer
      val check = new CooldownCheck[Unit](1.minute, NullStatsReceiver, timer)

      assert(check(Some(())).isDefined)
      assert(check(Some(())).isEmpty)

      tc.advance(61.seconds)
      timer.tick()

      assert(check(Some(())).isDefined)
      assert(check(Some(())).isEmpty)
    }
  }
}

@RunWith(classOf[JUnitRunner])
class OutlierCheckTest extends FunSuite {
  test("fail unless enough outliers are encountered") {
    val rate = Var(10)
    val check = new OutlierCheck(rate, 2, 0.1)

    assert(check(Some(Seq())).isEmpty)
    assert(check(Some(Seq(10, 10, 10))).isEmpty)
    assert(check(Some(Seq(1, 1, 10, 10))).isEmpty)

    assert(check(Some(Seq(1, 1))).isDefined)
    assert(check(Some(Seq(10, 10, 1, 1))).isDefined)

    // these fall within the 0.1 threshold, thus shouldn't be counted
    assert(check(Some(Seq(9, 9))).isEmpty)
  }
}

@RunWith(classOf[JUnitRunner])
class CalculateSampleRateTest extends FunSuite {
  test("calculates the discounted average of a series of numbers") {
    assert(DiscountedAverage.calculate(Seq(10, 5, 0), 1.0) === 5.0)

    val n = DiscountedAverage.calculate(Seq(10, 5, 0), 0.5)
    assert(DiscountedAverage.truncate(n) === 7.142)
  }

  test("calculates a discounted average based on the current req rate and sample rate") {
    val tgtReqRate = Var(100)
    val sampleRate = Var(1.0)
    val calc = new CalculateSampleRate(tgtReqRate, sampleRate, DiscountedAverage, 0.05, 1.0)
    assert(calc(Some(Seq(1000, 1000, 1000))) === Some(0.1))
  }
}
