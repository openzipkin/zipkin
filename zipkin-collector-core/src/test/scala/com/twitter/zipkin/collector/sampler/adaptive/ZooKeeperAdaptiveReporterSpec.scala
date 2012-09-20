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
package com.twitter.zipkin.collector.sampler.adaptive

import com.twitter.common.zookeeper.{Group, ZooKeeperClient}
import com.twitter.zipkin.config.sampler.adaptive.ZooKeeperAdaptiveSamplerConfig
import com.twitter.zipkin.config.sampler.AdjustableRateConfig
import com.twitter.conversions.time._
import com.twitter.ostrich.stats.Counter
import com.twitter.util.Timer
import org.apache.zookeeper.{CreateMode, ZooKeeper}
import org.apache.zookeeper.ZooDefs.Ids
import org.apache.zookeeper.data.Stat
import org.specs.mock.{ClassMocker, JMocker}
import org.specs.Specification

class ZooKeeperAdaptiveReporterSpec extends Specification with JMocker with ClassMocker {

  val samplerTimer = mock[Timer]

  "ZooKeeperAdaptiveReporter" should {
    val zk = mock[ZooKeeper]
    val _zkClient = mock[ZooKeeperClient]
    val _gm = mock[Group.Membership]
    val _path = ""
    val _updateInterval = 5.seconds
    val _reportInterval = 30.seconds
    val _reportWindow = 1.minute

    val _config = new ZooKeeperAdaptiveSamplerConfig {
      var client = _zkClient
      var sampleRate: AdjustableRateConfig = null
      var storageRequestRate: AdjustableRateConfig = null
      var taskTimer = samplerTimer

      reportPath = _path
    }

    val _counter = mock[Counter]

    val stat = mock[Stat]
    val nullStat: Stat = null

    val _memberId = "1"
    val fullPath = _path + "/" + _memberId

    def adaptiveReporter = new ZooKeeperAdaptiveReporter {
      val config         = _config
      val gm             = _gm
      val counter        = _counter
      val updateInterval = _updateInterval
      val reportInterval = _reportInterval
      val reportWindow   = _reportWindow

      memberId = _memberId
    }

    "create ephemeral node" in {
      val reporter = adaptiveReporter

      expect {
        one(_gm).getMemberId willReturn _memberId
        one(_zkClient).get willReturn zk
        one(zk).exists(fullPath, false) willReturn nullStat
        one(zk).create(fullPath, "0".getBytes, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL)
      }

      reporter.report()
    }

    "report" in {
      val reporter = adaptiveReporter

      expect {
        2.of(_gm).getMemberId willReturn _memberId
        2.of(_zkClient).get willReturn zk
        2.of(zk).exists(fullPath, false) willReturn stat

        1.of(_counter).apply() willReturn 0
        1.of(_counter).apply() willReturn 5

        1.of(zk).setData(fullPath, "0.0".getBytes, -1)
        1.of(zk).setData(fullPath, "5.0".getBytes, -1)
      }

      reporter.update()
      reporter.report()

      reporter.update()
      reporter.report()
    }
  }
}
