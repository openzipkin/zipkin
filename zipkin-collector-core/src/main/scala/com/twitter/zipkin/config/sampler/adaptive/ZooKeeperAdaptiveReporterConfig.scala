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

import com.twitter.zipkin.collector.sampler.adaptive.ZooKeeperAdaptiveReporter
import com.twitter.common.zookeeper.Group
import com.twitter.conversions.time._
import com.twitter.ostrich.stats.Stats
import com.twitter.util.Config

trait ZooKeeperAdaptiveReporterConfig extends Config[ZooKeeperAdaptiveReporter] {

  var config: ZooKeeperAdaptiveSamplerConfig

  var groupMembership: Group.Membership

  /**
   * Stats key to report
   */
  var reportKey = "cassandra.write_request_counter"

  /**
   * Frequency to update local data structures
   */
  var reporterUpdateInterval = 5.seconds

  /**
   * Frequency to report value to ZooKeeper
   */
  var reporterReportInterval = 30.seconds

  /**
   * Amount of data to keep in local data structures
   */
  var reportWindow = 1.minute

  def apply(): ZooKeeperAdaptiveReporter = {
    val counter = Stats.getCounter(reportKey)
    ZooKeeperAdaptiveReporter(
      config,
      groupMembership,
      counter,
      reporterUpdateInterval,
      reporterReportInterval,
      reportWindow
    )
  }
}
