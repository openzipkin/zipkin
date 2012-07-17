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
import com.twitter.zipkin.common.Span
import org.specs.mock.{ClassMocker, JMocker}
import org.specs.Specification

class ZooKeeperGlobalSamplerSpec extends Specification with JMocker with ClassMocker {

  def span(traceId: Long): Span = span(traceId, false)

  def span(traceId: Long, debug: Boolean): Span = {
    Span(traceId, "bah", 0L, None, List(), Seq(), debug)
  }

  "Sample" should {

    "keep 10% of traces" in {
      val sampleRate = 0.1
      val zkConfig = mock[AdjustableRateConfig]
      expect {
        allowing(zkConfig).get willReturn sampleRate
      }
      val sampler = new ZooKeeperGlobalSampler(zkConfig)

      sampler(span(Long.MinValue)) mustEqual false
      sampler(span(-1)) mustEqual true
      sampler(span(0)) mustEqual true
      sampler(span(1)) mustEqual true
      sampler(span(Long.MaxValue)) mustEqual false
    }

    "let pass if debug flag is set" in {
      val sampleRate = 0
      val zkConfig = mock[AdjustableRateConfig]
      expect {
        allowing(zkConfig).get willReturn sampleRate
      }
      val sampler = new ZooKeeperGlobalSampler(zkConfig)

      sampler(span(Long.MinValue, true)) mustEqual true
      sampler(span(-1, true)) mustEqual true
      sampler(span(0, true)) mustEqual true
      sampler(span(1, true)) mustEqual true
      sampler(span(Long.MaxValue, true)) mustEqual true
    }

    "drop all traces" in {
      val zkConfig = mock[AdjustableRateConfig]
      expect {
        allowing(zkConfig).get willReturn 0
      }
      val sampler = new ZooKeeperGlobalSampler(zkConfig)

      sampler(span(Long.MinValue)) mustEqual false
      sampler(span(Long.MinValue + 1))
      -5000 to 5000 foreach { i =>
        sampler(span(i)) mustEqual false
      }
      sampler(span(Long.MaxValue)) mustEqual false
    }

    "keep all traces" in {
      val zkConfig = mock[AdjustableRateConfig]
      expect {
        allowing(zkConfig).get willReturn 1
      }
      val sampler = new ZooKeeperGlobalSampler(zkConfig)

      sampler(span(Long.MinValue)) mustEqual true
      -5000 to 5000 foreach { i =>
        sampler(span(i)) mustEqual true
      }
      sampler(span(Long.MinValue)) mustEqual true
    }

  }
}
