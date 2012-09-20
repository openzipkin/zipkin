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
package com.twitter.zipkin.config.sampler.adaptive

import com.twitter.zipkin.collector.sampler.adaptive.ZooKeeperAdaptiveSampler
import com.twitter.zipkin.config.sampler.{AdaptiveSamplerConfig, AdjustableRateConfig}
import com.twitter.common.zookeeper.{Group, ZooKeeperClient}
import com.twitter.util.Timer
import org.apache.zookeeper.ZooDefs.Ids

trait ZooKeeperAdaptiveSamplerConfig extends AdaptiveSamplerConfig {

  var client: ZooKeeperClient

  var sampleRate: AdjustableRateConfig

  var storageRequestRate: AdjustableRateConfig

  var taskTimer: Timer

  /**
   * Path to the ZooKeeper group a collector should report to
   */
  var reportPath = "/twitter/service/zipkin/adaptivesampler/report"

  /**
   * ZooKeeper node name prefix for report members
   */
  var reportPrefix = "adaptivereporter_"

  def apply() = {
    val adaptiveConfig = this
    val reportGroup = new Group(client, Ids.OPEN_ACL_UNSAFE, reportPath, reportPrefix)
    val reportGroupMembership = reportGroup.join()
    val reporter = new ZooKeeperAdaptiveReporterConfig {
      var config = adaptiveConfig
      var groupMembership = reportGroupMembership
    }

    val leader = new ZooKeeperAdaptiveLeaderConfig {
      var config = adaptiveConfig
      var reporterGroup = reportGroup
    }
    ZooKeeperAdaptiveSampler(taskTimer, leader(), reporter())
  }
}
