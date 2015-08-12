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

import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class AdjustableRateConfigSpec extends FunSuite with Matchers with MockitoSugar {

  val sampleRateConfig = mock[AdjustableRateConfig]
  val sr = 0.3

  test("not issue zk calls on set") {
    val config = new ReadOnlyAdjustableRateConfig(sampleRateConfig)
    config.set(sr)

    verifyZeroInteractions(sampleRateConfig)
  }

  test("not issue zk calls on setIfNotExists") {
    val config = new ReadOnlyAdjustableRateConfig(sampleRateConfig)
    config.set(sr)

    verifyZeroInteractions(sampleRateConfig)
  }
}
