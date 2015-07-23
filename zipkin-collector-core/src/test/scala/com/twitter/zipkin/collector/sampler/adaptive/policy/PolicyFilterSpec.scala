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
package com.twitter.zipkin.collector.sampler.adaptive.policy

import com.twitter.conversions.time._
import com.twitter.util.{MockTimer, Time}
import com.twitter.zipkin.collector.sampler.adaptive.BoundedBuffer
import com.twitter.zipkin.config.sampler.AdjustableRateConfig
import com.twitter.zipkin.config.sampler.adaptive.ZooKeeperAdaptiveSamplerConfig
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class PolicyFilterSpec extends FunSuite with Matchers with MockitoSugar {

  val _config = mock[ZooKeeperAdaptiveSamplerConfig]
  val _storageRequestRate = mock[AdjustableRateConfig]

  test("ValidLatestValueFilter and SufficientDataFilter") {
    val filter1 = new ValidLatestValueFilter
    val filter2 = new SufficientDataFilter(1.minute, 30.seconds)
    val buf = new BoundedBuffer {
      val maxLength = 5
    }

    val composed = filter1 andThen filter2

    composed(buf) should be(None) // No values => fail
    buf.update(1)
    composed(buf) should be(None) // valid value, not sufficient => fail
    buf.update(-1)
    composed(buf) should be(None) // invalid value, sufficient => fail
    buf.update(1)
    composed(buf) should be(Some(buf)) // valid value, sufficient => pass
  }

  test("notify of sample rate change") {
    val mock1 = mock[Seq[Int]]
    val filter1 = new ValidLatestValueFilter {
      override def change(sampleRate: Double) {
        mock1.length
      }
    }
    val filter2 = new SufficientDataFilter(1.minute, 30.seconds) {
      override def change(sampleRate: Double) {
        mock1.length
      }
    }

    val composed = filter1 andThen filter2

    composed.notifyChange(0.2)

    verify(mock1, times(2)).length
  }

  val storageRateFilter = new StorageRequestRateFilter(_config)
  val buf = new BoundedBuffer {
    val maxLength = 5
  }

  test("fail if storage request rate is negative") {
    when(_config.storageRequestRate) thenReturn _storageRequestRate
    when(_storageRequestRate.get) thenReturn -1

    storageRateFilter(buf) should be(None)
  }

  test("fail if storage request rate is zero") {
    when(_config.storageRequestRate) thenReturn _storageRequestRate
    when(_storageRequestRate.get) thenReturn 0

    storageRateFilter(buf) should be(None)
  }

  test("pass if storage request rate is positive") {
    when(_config.storageRequestRate) thenReturn _storageRequestRate
    when(_storageRequestRate.get) thenReturn 1

    storageRateFilter(buf) should be(Some(buf))
  }

  val validLatestFilter = new ValidLatestValueFilter

  test("fail if latest value is negative") {
    val buf = new BoundedBuffer {
      val maxLength = 5
    }
    buf.update(-1)
    validLatestFilter(buf) should be(None)
  }

  test("fail if latest value is zero") {
    val buf = new BoundedBuffer {
      val maxLength = 5
    }
    buf.update(0)
    validLatestFilter(buf) should be(None)
  }

  test("pass if latest value is positive") {
    val buf = new BoundedBuffer {
      val maxLength = 5
    }
    buf.update(1)
    validLatestFilter(buf) should be(Some(buf))
  }

  test("pass if has enough data") {
    val filter = new SufficientDataFilter(dataSufficient = 1.minute, pollInterval = 30.seconds)

    val buf = new BoundedBuffer {
      val maxLength = 5
    }
    filter(buf) should be(None)
    buf.update(1.0)
    filter(buf) should be(None)
    buf.update(2.0)
    filter(buf) should be(Some(buf))
  }

  test("pass if enough outliers have been encountered") {
    val target = 10
    val filter = new OutlierFilter(_config, 0.1, 1.minute, 30.seconds)
    val buf = new BoundedBuffer {
      val maxLength = 5
    }

    when(_config.storageRequestRate) thenReturn _storageRequestRate
    when(_storageRequestRate.get) thenReturn target

    filter(buf) should be(None)
    buf.update(1.0)
    filter(buf) should be(None)
    buf.update(1.0)
    filter(buf) should be(Some(buf))
  }

  test("only pass if enough time has passed since last change") {
    Time.withCurrentTimeFrozen { tc =>
      val timer = new MockTimer
      val filter = new CooldownFilter(1.minute, timer)
      val buf = new BoundedBuffer {
        val maxLength = 5
      }

      filter(buf) should be(Some(buf))

      filter.notifyChange(0.2)
      filter(buf) should be(None)

      tc.advance(61.seconds)
      timer.tick()
      filter(buf) should be(Some(buf))
    }
  }
}
