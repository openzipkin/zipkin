package com.twitter.zipkin.collector.builder

import com.twitter.conversions.time._
import com.twitter.util.{Timer, FuturePool}
import com.twitter.zipkin.builder.{ZooKeeperClientBuilder, Builder}
import com.twitter.zipkin.config.sampler.{ZooKeeperAdjustableRateConfig, MutableAdjustableRateConfig, AdjustableRateConfig}
import com.twitter.zk.{RetryPolicy, ZkClient, CommonConnector}
import org.apache.zookeeper.ZooDefs.Ids
import scala.collection.JavaConverters._

object Adjustable {
  def local(default: Double) = new Builder[AdjustableRateConfig] {
    def apply() = {
      new MutableAdjustableRateConfig(default)
    }
  }

  def zookeeper(
    zkClientBuilder: ZooKeeperClientBuilder,
    configPath: String,
    key: String,
    defaultValue: Double)(implicit timer: Timer) = new Builder[AdjustableRateConfig] {

    def apply() = {
      val zkClient = zkClientBuilder.apply()
      val connector = CommonConnector(zkClient)(FuturePool.defaultPool)

      val zClient = ZkClient(connector)
        .withAcl(Ids.OPEN_ACL_UNSAFE.asScala)
        .withRetryPolicy(RetryPolicy.Exponential(1.second, 1.5)(timer))

      ZooKeeperAdjustableRateConfig(zClient, configPath, key, defaultValue)
    }
  }
}
