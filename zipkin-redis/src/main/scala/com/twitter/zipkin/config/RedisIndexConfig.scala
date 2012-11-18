/*
 * Copyright 2012 Tumblr Inc.
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

package com.twitter.zipkin.config

import com.twitter.conversions.time.intToTimeableNumber
import com.twitter.finagle.redis.Client
import com.twitter.util.Duration
import com.twitter.zipkin.storage.redis.RedisIndex

/**
 * RedisIndexConfig has sane defaults, except you must specify your host and port.
 */
trait RedisIndexConfig extends IndexConfig {
  lazy val _client: Client = Client("%s:%d".format(host, port))

  val tracesTimeToLive: Duration = 7.days
  val port: Int
  val host: String

  /**
   * The canonical way to make a new RedisIndex
   */
  def apply(): RedisIndex = new RedisIndex {
    val database = _client
    val ttl = Some(tracesTimeToLive)
  }
}