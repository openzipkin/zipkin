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

import com.twitter.zipkin.config.sampler.AdjustableRateConfig
import org.specs.mock.{ClassMocker, JMocker}
import org.specs.Specification

class ZooKeeperGlobalSamplerSpec extends Specification with JMocker with ClassMocker {
  "Sample" should {

    "keep 10% of traces" in {
      val sampleRate = 0.1
      val zkConfig = mock[AdjustableRateConfig]
      expect {
        allowing(zkConfig).get willReturn sampleRate
      }
      val sampler = new ZooKeeperGlobalSampler(zkConfig)

      sampler(Long.MinValue) mustEqual false
      sampler(-1) mustEqual true
      sampler(0) mustEqual true
      sampler(1) mustEqual true
      sampler(Long.MaxValue) mustEqual false
    }

    "drop all traces" in {
      val zkConfig = mock[AdjustableRateConfig]
      expect {
        allowing(zkConfig).get willReturn 0
      }
      val sampler = new ZooKeeperGlobalSampler(zkConfig)

      sampler(Long.MinValue) mustEqual false
      sampler(Long.MinValue + 1)
      -5000 to 5000 foreach { i =>
        sampler(i) mustEqual false
      }
      sampler(Long.MaxValue) mustEqual false
    }

    "keep all traces" in {
      val zkConfig = mock[AdjustableRateConfig]
      expect {
        allowing(zkConfig).get willReturn 1
      }
      val sampler = new ZooKeeperGlobalSampler(zkConfig)

      sampler(Long.MinValue) mustEqual true
      -5000 to 5000 foreach { i =>
        sampler(i) mustEqual true
      }
      sampler(Long.MinValue) mustEqual true
    }

  }
}
