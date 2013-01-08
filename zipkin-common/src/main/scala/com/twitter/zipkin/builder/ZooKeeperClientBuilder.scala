/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.builder

import com.twitter.common.net.InetSocketAddressHelper
import com.twitter.common.quantity.{Time, Amount}
import com.twitter.common.zookeeper.{ZooKeeperUtils, ZooKeeperClient}
import com.twitter.ostrich.admin.{ServiceTracker, Service => OstrichService}
import scala.collection.JavaConverters._

case class ZooKeeperClientBuilder(
  hosts: Seq[String],
  port: Int = 2181,
  sessionTimeout: Amount[java.lang.Integer, Time] = ZooKeeperUtils.DEFAULT_ZK_SESSION_TIMEOUT
) extends Builder[ZooKeeperClient] {

  def port(p: Int)                                      : ZooKeeperClientBuilder = copy(port = p)
  def sessionTimeout(s: Amount[java.lang.Integer, Time]): ZooKeeperClientBuilder = copy(sessionTimeout = s)

  def apply() = {
    val hostPorts = hosts map { "%s:%d".format(_, port) }
    val socketAddress = InetSocketAddressHelper.convertToSockets(hostPorts.asJava)
    val client = new ZooKeeperClient(sessionTimeout, socketAddress)

    ServiceTracker.register(new OstrichService {
      def start() {}
      def shutdown { client.close() }
    })

    client
  }
}
