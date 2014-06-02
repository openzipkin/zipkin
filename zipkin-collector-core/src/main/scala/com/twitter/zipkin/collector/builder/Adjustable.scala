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

import com.twitter.conversions.time._
import com.twitter.util.{Timer, FuturePool}
import com.twitter.zipkin.builder.{ZooKeeperClientBuilder, Builder}
import com.twitter.zipkin.config.sampler.{ZooKeeperAdjustableRateConfig, MutableAdjustableRateConfig, AdjustableRateConfig}
import com.twitter.zk.{RetryPolicy, ZkClient, CommonConnector}
import org.apache.zookeeper.ZooDefs.Ids
import scala.collection.JavaConverters._

object Adjustable {

  /**
   * Builder for a locally adjustable rate
   *
   * @param default default value
   * @return
   */
  def local(default: Double) = new Builder[AdjustableRateConfig] {
    def apply() = {
      new MutableAdjustableRateConfig(default)
    }
  }

  /**
   * Builder for an adjustable rate stored in ZooKeeper
   *
   * @param zkClientBuilder ZooKeeperClient builder
   * @param configPath path in ZooKeeper to store the value
   * @param key name of the value in ZooKeeper
   * @param defaultValue default value
   * @param timer
   * @return
   */
  def zookeeper(
    zkClientBuilder: ZooKeeperClientBuilder,
    configPath: String,
    key: String,
    defaultValue: Double)(implicit timer: Timer) = new Builder[AdjustableRateConfig] {

    def apply() = {
      val zkClient = zkClientBuilder.apply()
      val connector = CommonConnector(zkClient)(FuturePool.unboundedPool)

      val zClient = ZkClient(connector)
        .withAcl(Ids.OPEN_ACL_UNSAFE.asScala)
        .withRetryPolicy(RetryPolicy.Exponential(1.second, 1.5)(timer))

      ZooKeeperAdjustableRateConfig(zClient, configPath, key, defaultValue)
    }
  }
}
