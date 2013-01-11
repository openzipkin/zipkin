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
package com.twitter.zipkin.collector.builder

import com.twitter.util.Timer
import com.twitter.zipkin.builder.{ZooKeeperClientBuilder, Builder}
import com.twitter.zipkin.config.sampler.{AdaptiveSamplerConfig, AdjustableRateConfig}
import com.twitter.zipkin.config.sampler.adaptive.ZooKeeperAdaptiveSamplerConfig

object Adaptive {

  /**
   * TODO this can be combined with ZooKeeperAdaptiveSamplerConfig
   */
  def zookeeper(
    zkClientBuilder: ZooKeeperClientBuilder,
    sampleRateBuilder: Builder[AdjustableRateConfig],
    storageRequestRateBuilder: Builder[AdjustableRateConfig]
  )(implicit timer: Timer) = new Builder[AdaptiveSamplerConfig] {

    def apply() = {

      val zkClient                 = zkClientBuilder.apply()
      val sampleRateConfig         = sampleRateBuilder.apply()
      val storageRequestRateConfig = storageRequestRateBuilder.apply()

      new ZooKeeperAdaptiveSamplerConfig {
        var client = zkClient
        var sampleRate = sampleRateConfig
        var storageRequestRate = storageRequestRateConfig
        var taskTimer = timer
      }
    }
  }
}
