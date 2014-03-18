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
import com.twitter.zipkin.config.sampler.adaptive.ZooKeeperAdaptiveSamplerConfig
import com.twitter.zipkin.config.sampler.AdjustableRateConfig
import com.twitter.zipkin.collector.sampler.adaptive.BoundedBuffer
import org.specs.Specification
import org.specs.mock.{JMocker, ClassMocker}
import com.twitter.util.{MockTimer, Time}

class PolicyFilterSpec extends Specification with JMocker with ClassMocker {

  val _config = mock[ZooKeeperAdaptiveSamplerConfig]
  val _storageRequestRate = mock[AdjustableRateConfig]

  "PolicyFilter" should {

    "compose" in {
      "ValidLatestValueFilter and SufficientDataFilter" in {
        val filter1 = new ValidLatestValueFilter
        val filter2 = new SufficientDataFilter(1.minute, 30.seconds)
        val buf = new BoundedBuffer { val maxLength = 5 }

        val composed = filter1 andThen filter2

        composed(buf) mustEqual None      // No values => fail
        buf.update(1)
        composed(buf) mustEqual None      // valid value, not sufficient => fail
        buf.update(-1)
        composed(buf) mustEqual None     // invalid value, sufficient => fail
        buf.update(1)
        composed(buf) mustEqual Some(buf) // valid value, sufficient => pass
      }
    }

    "notify of sample rate change" in {
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

      expect {
        2.of(mock1).length
      }

      composed.notifyChange(0.2)
    }
  }

  "StorageRequestRateFilter" should {
    val filter = new StorageRequestRateFilter(_config)
    val buf = new BoundedBuffer { val maxLength = 5 }

    "fail if storage request rate is negative" in {
      expect {
        1.of(_config).storageRequestRate willReturn _storageRequestRate
        1.of(_storageRequestRate).get willReturn -1
      }

      filter(buf) mustEqual None
    }

    "fail if storage request rate is zero" in {
      expect {
        1.of(_config).storageRequestRate willReturn _storageRequestRate
        1.of(_storageRequestRate).get willReturn 0
      }

      filter(buf) mustEqual None
    }

    "pass if storage request rate is positive" in {
      expect {
        1.of(_config).storageRequestRate willReturn _storageRequestRate
        1.of(_storageRequestRate).get willReturn 1
      }

      filter(buf) mustEqual Some(buf)
    }
  }

  "ValidLatestValueFilter" should {
    val filter = new ValidLatestValueFilter

    "fail if latest value is negative" in {
      val buf = new BoundedBuffer { val maxLength = 5 }
      buf.update(-1)
      filter(buf) mustEqual None
    }

    "fail if latest value is zero" in {
      val buf = new BoundedBuffer { val maxLength = 5 }
      buf.update(0)
      filter(buf) mustEqual None
    }

    "pass if latest value is positive" in {
      val buf = new BoundedBuffer { val maxLength = 5 }
      buf.update(1)
      filter(buf) mustEqual Some(buf)
    }
  }

  "SufficientDataFilter" should {
    val filter = new SufficientDataFilter(dataSufficient = 1.minute, pollInterval = 30.seconds)
    "pass if has enough data" in {
      val buf = new BoundedBuffer { val maxLength = 5 }
      filter(buf) mustEqual None
      buf.update(1.0)
      filter(buf) mustEqual None
      buf.update(2.0)
      filter(buf) mustEqual Some(buf)
    }
  }

  "OutlierFilter" should {
    "pass if enough outliers have been encountered" in {
      val target = 10
      val filter = new OutlierFilter(_config, 0.1, 1.minute, 30.seconds)
      val buf = new BoundedBuffer { val maxLength = 5 }

      expect {
        atLeast(1).of(_config).storageRequestRate willReturn _storageRequestRate
        atLeast(1).of(_storageRequestRate).get willReturn target
      }

      filter(buf) mustEqual None
      buf.update(1.0)
      filter(buf) mustEqual None
      buf.update(1.0)
      filter(buf) mustEqual Some(buf)
    }
  }

  "CooldownFilter" should {
    "only pass if enough time has passed since last change" in Time.withCurrentTimeFrozen { tc =>
      val timer = new MockTimer
      val filter = new CooldownFilter(1.minute, timer)
      val buf = new BoundedBuffer { val maxLength = 5 }

      filter(buf) mustEqual Some(buf)

      filter.notifyChange(0.2)
      filter(buf) mustEqual None

      tc.advance(61.seconds)
      timer.tick()
      filter(buf) mustEqual Some(buf)
    }
  }
}
