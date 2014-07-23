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
package com.twitter.zipkin.redis

import com.twitter.conversions.time._
import com.twitter.finagle.redis.Client
import com.twitter.finagle.redis.util.StringToChannelBuffer
import com.twitter.util.Duration
import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.storage.redis.RedisIndex
import com.twitter.zipkin.storage.Index
import com.twitter.util.Await
import com.twitter.util.Future

case class IndexBuilder(
  host: String,
  port: Int,
  ttl: Duration = 7.days,
  authPassword: Option[String] = None
) extends Builder[Index] { self =>

  def ttl(t: Duration): IndexBuilder = copy(ttl = t)

  def apply() = {
    val client = Client("%s:%d".format(host, port))
    val authenticate = authPassword.map(p => client.auth(StringToChannelBuffer(p))) getOrElse Future.Done
    Await.result(authenticate before Future.value(new RedisIndex {
      val database = client
      val ttl = Some(self.ttl)
    }), 10.seconds)
  }
}
