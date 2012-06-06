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
package com.twitter.zipkin.config.sampler

import com.twitter.concurrent.Broker
import com.twitter.util.Try
import com.twitter.zk.{ZOp, ZNode, ZkClient}
import org.apache.zookeeper.{Watcher, WatchedEvent}
import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}


class ZooKeeperAdjustableRateConfigSpec extends Specification with JMocker with ClassMocker {

  "ZooKeeperAdjustableRateConfig" should {
    val KeySampleRate = "samplerate"

    val client = mock[ZkClient]
    val mockedNode = mock[ZNode]
    val dataop = mock[ZOp[ZNode.Data]]

    val sessionBroker = new Broker[WatchedEvent]

    val configPath = "/twitter/service/zipkin/config"
    val fullPath = configPath + "/" + KeySampleRate

    val default = ZooKeeperSampleRateConfig.Default

    def config = new ZooKeeperAdjustableRateConfig(
      client,
      configPath,
      KeySampleRate,
      default
    )

    "no initialization returns default" in {
      val c = config
      c.get must_== default
    }

    "data monitor works" in {
      val newVal = 0.1
      val broker = new Broker[Try[ZNode.Data]]
      expect {
        // Init
        one(client).apply(fullPath) willReturn mockedNode

        // Monitor
        one(mockedNode).getData willReturn dataop
        one(dataop).monitor() willReturn broker.recv

        // Set
        one(mockedNode).setData(any[Array[Byte]], anyInt)
        one(mockedNode).path willReturn fullPath
        allowingMatch(mockedNode, "zkClient")
      }
      val c = config

      // Monitor
      c.monitor()

      c.get must_== default

      // Set
      c.set(newVal)

      // Send set value via broker
      broker.send(Try(ZNode.Data(mockedNode, null, newVal.toString.getBytes))).sync()

      c.get must_== newVal
    }
  }
}
