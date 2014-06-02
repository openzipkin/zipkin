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

import com.twitter.common.zookeeper.{ZooKeeperClient, Group}
import com.twitter.conversions.time._
import com.twitter.util.Timer
import com.twitter.zipkin.collector.sampler.adaptive.policy.LeaderPolicy
import com.twitter.zipkin.config.sampler.adaptive.ZooKeeperAdaptiveSamplerConfig
import com.twitter.zipkin.config.sampler.AdjustableRateConfig
import org.apache.zookeeper.ZooKeeper
import org.specs.mock.{ClassMocker, JMocker}
import org.specs.Specification
import scala.collection.JavaConverters._

class ZooKeeperAdaptiveLeaderSpec extends Specification with JMocker with ClassMocker {
  val samplerTimer = mock[Timer]

  "ZooKeeperAdaptiveLeader" should {
    val _zk                       = mock[ZooKeeper]
    val _zkClient                 = mock[ZooKeeperClient]
    val _buf                      = mock[BoundedBuffer]
    val _reportGroup              = mock[Group]
    val _leaderPolicy             = mock[LeaderPolicy[BoundedBuffer]]

    val _config = mock[ZooKeeperAdaptiveSamplerConfig]
    val _sampleRateConfig = mock[AdjustableRateConfig]

    val _leaderGroup: Group = null
    val _windowSize = 30.minutes
    val _windowSufficient = 10.minutes
    val _pollInterval = 1.minute

    val _reportPath = "/twitter/service/zipkin/adaptivesampler/report"

    def adaptiveLeader: ZooKeeperAdaptiveLeader =
      new ZooKeeperAdaptiveLeader {
        val config                      = _config
        val reportGroup                 = _reportGroup
        val leaderGroup                 = _leaderGroup
        val bufferSize                  = _windowSize
        val windowSufficient            = _windowSufficient
        val pollInterval                = _pollInterval
        val leaderPolicy                = _leaderPolicy

        override lazy val buf = _buf
      }

    "update" in {
      val leader = adaptiveLeader

      val ids = Seq[String]("1", "2", "3")
      val sum = 600.0
      val expectedUpdate = sum.toLong

      expect {
        1.of(_config).client willReturn _zkClient
        1.of(_zkClient).get willReturn _zk
        one(_reportGroup).getMemberIds willReturn ids.asJava
        ids.foreach { id =>
          one(_reportGroup).getMemberPath(id) willReturn (_reportPath + "/" + id)
        }

        one(_zk).getData(_reportPath + "/1", true, null) willReturn "100".getBytes
        one(_zk).getData(_reportPath + "/2", true, null) willReturn "200".getBytes
        one(_zk).getData(_reportPath + "/3", true, null) willReturn "300".getBytes

        one(_buf).update(expectedUpdate)
      }

      leader.update()
    }

    "do nothing if policy returns None" in {
      val leader = adaptiveLeader

      expect {
        1.of(_leaderPolicy).apply(Some(_buf)) willReturn None
      }

      leader.lead()
    }

    "adjust sample rate if policy returns valid option" in {
      val leader = adaptiveLeader
      val newSampleRate = 0.1

      expect {
        1.of(_config).sampleRate willReturn _sampleRateConfig
        1.of(_leaderPolicy).apply(Some(_buf)) willReturn Some(newSampleRate)
        1.of(_sampleRateConfig).set(newSampleRate)
        1.of(_leaderPolicy).notifyChange(newSampleRate)
      }

      leader.lead()
    }

    "truncate" in {
      ZooKeeperAdaptiveLeader.truncate(0.1110) mustEqual 0.111
      ZooKeeperAdaptiveLeader.truncate(0.1111) mustEqual 0.111
      ZooKeeperAdaptiveLeader.truncate(0.1115) mustEqual 0.111
      ZooKeeperAdaptiveLeader.truncate(0.1119) mustEqual 0.111
      ZooKeeperAdaptiveLeader.truncate(0.1120) mustEqual 0.112
    }
  }
}
