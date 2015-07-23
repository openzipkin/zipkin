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
import com.twitter.conversions.time._
import com.twitter.ostrich.stats.Counter
import com.twitter.util.Timer
import com.twitter.zipkin.config.sampler.AdjustableRateConfig
import com.twitter.zipkin.config.sampler.adaptive.ZooKeeperAdaptiveSamplerConfig
import org.apache.zookeeper.ZooDefs.Ids
import org.apache.zookeeper.data.Stat
import org.apache.zookeeper.{CreateMode, ZooKeeper}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class ZooKeeperAdaptiveReporterSpec extends FunSuite with Matchers with MockitoSugar {

  val samplerTimer = mock[Timer]

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

  test("create ephemeral node") {
    val reporter = adaptiveReporter

    when(_gm.getMemberId) thenReturn _memberId
    when(_zkClient.get) thenReturn zk
    when(zk.exists(fullPath, false)) thenReturn nullStat

    reporter.report()

    verify(zk).create(fullPath, "0".getBytes, Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL)
  }

  test("report") {
    val reporter = adaptiveReporter

    when(_gm.getMemberId) thenReturn _memberId
    when(_zkClient.get) thenReturn zk
    when(zk.exists(fullPath, false)) thenReturn stat

    when(_counter.apply()) thenReturn 0

    reporter.update()
    reporter.report()

    verify(zk).setData(fullPath, "0.0".getBytes, -1)

    when(_counter.apply()) thenReturn 5

    reporter.update()
    reporter.report()

    verify(zk).setData(fullPath, "5.0".getBytes, -1)
  }
}
