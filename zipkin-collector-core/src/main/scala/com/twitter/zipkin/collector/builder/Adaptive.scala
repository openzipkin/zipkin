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
