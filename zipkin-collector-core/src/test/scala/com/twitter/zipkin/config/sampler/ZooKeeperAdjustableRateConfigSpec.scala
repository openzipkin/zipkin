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
import com.twitter.zk.{ZNode, ZOp, ZkClient}
import org.apache.zookeeper.WatchedEvent
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class ZooKeeperAdjustableRateConfigSpec extends FunSuite with Matchers with MockitoSugar {

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

  test("no initialization returns default") {
    val c = config
    c.get should be (default)
  }

  test("data monitor works") {
    val newVal = 0.1
    val broker = new Broker[Try[ZNode.Data]]

    when(client.apply(fullPath)) thenReturn mockedNode

    when(mockedNode.getData) thenReturn dataop
    when(dataop.monitor()) thenReturn broker.recv
    when(mockedNode.path) thenReturn fullPath

    val c = config

    // Monitor
    c.monitor()

    c.get should be (default)

    // Set
    c.set(newVal)

    // Send set value via broker
    broker.send(Try(ZNode.Data(mockedNode, null, newVal.toString.getBytes))).sync()

    c.get should be (newVal)

    verify(mockedNode).setData(any(), any())
  }
}
