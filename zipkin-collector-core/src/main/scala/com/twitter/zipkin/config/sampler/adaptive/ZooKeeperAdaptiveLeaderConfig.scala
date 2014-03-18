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

import com.twitter.common.zookeeper.Group
import com.twitter.conversions.time._
import com.twitter.util.Config
import com.twitter.zipkin.collector.sampler.adaptive.policy._
import com.twitter.zipkin.collector.sampler.adaptive.{BoundedBuffer, ZooKeeperAdaptiveLeader}
import org.apache.zookeeper.ZooDefs.Ids

trait ZooKeeperAdaptiveLeaderConfig extends Config[ZooKeeperAdaptiveLeader] {

  var config: ZooKeeperAdaptiveSamplerConfig

  var reporterGroup: Group

  /**
   * Path to the ZooKeeper leader election group
   */
  var membersPath = "/twitter/service/zipkin/adaptivesampler/members"

  /**
   * Percentage threshold the change in sample rate must be for the leader
   * to change the sample rate. Prevents too small of changes.
   */
  val sampleRateThreshold = 0.05

  /**
   * Percentage threshold of StorageRequestRate outside which a value
   * is considered an outlier
   */
  var storageRequestRateThreshold = 0.15

  /**
   * Amount of leader data to keep in local data structures
   */
  var windowSize = 30.minutes

  /**
   * Amount of leader data sufficient to begin making decisions after
   * a cold start
   */
  var windowSufficient = 10.minutes

  /**
   * Frequency leader polls to get new request numbers and checks sample rate
   */
  var leaderPollInterval = 30.seconds

  /**
   * Amount of time of sustained outliers needed to trigger a sample rate change
   */
  var outlierThresholdInterval = 5.minutes

  /**
   * Amount of time of sustained inliers needed to trigger a sample rate change
   */
  var inlierThresholdInterval = 10.minutes

  /**
   * ZooKeeper node name prefix for leader election
   */
  var leaderPrefix = "adaptiveleader_"

  lazy val leaderPolicy: LeaderPolicy[BoundedBuffer] = {
    new StorageRequestRateFilter(config)                     andThen
    new ValidLatestValueFilter                               andThen
    new SufficientDataFilter(windowSufficient, leaderPollInterval) andThen
    new OutlierFilter(
      config,
      storageRequestRateThreshold,
      outlierThresholdInterval,
      leaderPollInterval) andThen
    new CooldownFilter(outlierThresholdInterval, config.taskTimer) andThen
    DiscountedAverageLeaderPolicy(config, sampleRateThreshold)
  }

  def apply() = {
    val leaderGroup = new Group(config.client, Ids.OPEN_ACL_UNSAFE, membersPath, leaderPrefix)
    ZooKeeperAdaptiveLeader(
      config,
      reporterGroup,
      leaderGroup,
      windowSize,
      leaderPollInterval,
      leaderPolicy
    )
  }
}
