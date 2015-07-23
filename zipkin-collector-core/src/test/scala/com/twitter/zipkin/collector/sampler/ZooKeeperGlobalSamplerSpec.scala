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
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class ZooKeeperGlobalSamplerSpec extends FunSuite with Matchers with MockitoSugar {
  val zkConfig = mock[AdjustableRateConfig]

  test("keep 10% of traces") {
    when(zkConfig.get) thenReturn 0.1

    val sampler = new ZooKeeperGlobalSampler(zkConfig)

    sampler(Long.MinValue) should be (false)
    sampler(-1) should be (true)
    sampler(0) should be (true)
    sampler(1) should be (true)
    sampler(Long.MaxValue) should be (false)
  }

  test("drop all traces") {
    when(zkConfig.get) thenReturn 0

    val sampler = new ZooKeeperGlobalSampler(zkConfig)

    sampler(Long.MinValue) should be (false)
    sampler(Long.MinValue + 1)
    -5000 to 5000 foreach { i =>
      sampler(i) should be (false)
    }
    sampler(Long.MaxValue) should be (false)
  }

  test("keep all traces") {
    when(zkConfig.get) thenReturn 1

    val sampler = new ZooKeeperGlobalSampler(zkConfig)

    sampler(Long.MinValue) should be (true)
    -5000 to 5000 foreach { i =>
      sampler(i) should be (true)
    }
    sampler(Long.MinValue) should be (true)
  }
}
