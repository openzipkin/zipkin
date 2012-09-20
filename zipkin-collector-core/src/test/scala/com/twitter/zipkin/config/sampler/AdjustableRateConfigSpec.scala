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
package com.twitter.zipkin.config.sampler

import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}

class AdjustableRateConfigSpec extends Specification with JMocker with ClassMocker {

  "ReadOnlyAdjustableRateConfig" should {
    val sampleRateConfig = mock[AdjustableRateConfig]
    val sr = 0.3

    "not issue zk calls on set" in {
      expect {}
      val config = new ReadOnlyAdjustableRateConfig(sampleRateConfig)
      config.set(sr)
    }

    "not issue zk calls on setIfNotExists" in {
      expect {}
      val config = new ReadOnlyAdjustableRateConfig(sampleRateConfig)
      config.set(sr)
    }
  }
}
