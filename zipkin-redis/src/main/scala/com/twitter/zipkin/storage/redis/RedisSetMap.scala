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

package com.twitter.zipkin.storage.redis

import com.twitter.finagle.redis.Client
import com.twitter.finagle.redis.protocol.ZRangeResults
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffer
import com.twitter.util.Duration

class RedisSetMap(database: Client, prefix: String, defaultTTL: Option[Duration]) {
  private[this] def preface(key: String) = "%s:%s".format(prefix, key)

  def add(key: String, buf: ChannelBuffer): Future[Unit] = database.sAdd(preface(key), List(buf)) flatMap { _ =>
    defaultTTL match {
      case Some(ttl) => database.expire(preface(key), ttl.inSeconds) flatMap (_ => Future.Unit)
      case None => Future.Unit
    }
  }

  def get(key: String): Future[Set[ChannelBuffer]] = {
    database.sMembers(preface(key)) map (_.toSet)
  }
}
