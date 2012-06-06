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

import com.twitter.common.quantity.{Time, Amount}
import com.twitter.common.zookeeper.ZooKeeperUtils
import com.twitter.conversions.time._
import com.twitter.util.Duration

trait ZooKeeperConfig {
  var servers: List[String] = List("localhost:2181")

  /* TODO Remove use of Commons libs in favor of util-zk, then we can get rid of the extra
     sessionTimeout variable */
  var sessionTimeout: Amount[java.lang.Integer, Time] = ZooKeeperUtils.DEFAULT_ZK_SESSION_TIMEOUT
  var sessionTimeoutDuration: Duration = 3.minutes

  def connectString: String = servers.reduceLeft(_ + "," + _)
}

