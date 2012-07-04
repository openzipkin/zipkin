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
package com.twitter.zipkin.config.zookeeper

import com.twitter.zipkin.common.ZooKeeperClientService
import com.twitter.common.net.InetSocketAddressHelper
import com.twitter.common.zookeeper.ZooKeeperClient
import com.twitter.logging.Logger
import com.twitter.ostrich.admin.ServiceTracker
import com.twitter.util.Config
import scala.collection.JavaConverters._

trait ZooKeeperClientConfig extends Config[ZooKeeperClient] {

  var config: ZooKeeperConfig

  val log = Logger.get(getClass.getName)

  def apply(): ZooKeeperClient = {
    log.info("Starting ZooKeeperClient")
    val socketAddress = InetSocketAddressHelper.convertToSockets(config.servers.asJava)
    val client = new ZooKeeperClient(config.sessionTimeout, socketAddress)
    ServiceTracker.register(new ZooKeeperClientService(client))
    client
  }
}
