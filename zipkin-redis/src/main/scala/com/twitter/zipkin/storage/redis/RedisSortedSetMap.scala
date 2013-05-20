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
import com.twitter.finagle.redis.protocol.{Limit, ZInterval, ZRangeResults}
import com.twitter.util.{Duration, Future}
import org.jboss.netty.buffer.{ChannelBuffers, ChannelBuffer}

/**
 * RedisSortedSetMap is a map from strings to sorted sets.
 * @param database the redis client to use
 * @param prefix the namespace of the sorted set
 * @param defaultTTL the timeout on the sorted set
 */
class RedisSortedSetMap(database: Client, prefix: String, defaultTTL: Option[Duration]) {
  private[this] def preface(key: String) = {
    val str = "%s:%s".format(prefix, key)
    ChannelBuffers.copiedBuffer(str)
  }

  /**
   * Adds a buffer with a score to the sorted set specified by key.
   */
  def add(key: String, score: Double, buffer: ChannelBuffer): Future[Unit] =
    database.zAdd(preface(key), score, buffer) flatMap { _ =>
      defaultTTL match {
        case Some(ttl) => database.expire(preface(key), ttl.inSeconds).unit
        case None => Future.Unit
      }
    }

  /**
   * Gets elements from a sorted set, in reverse order.
   * @param key specifies which sorted set
   * @param start items must have a score bigger than this
   * @param stop items must have a score smaller than this
   * @param count number of items to return
   */
  def get(key: String, start: Double, stop: Double, count: Long): Future[ZRangeResults] = {
    database.zRevRangeByScore(preface(key), ZInterval(stop), ZInterval(start), true, Some(Limit(0, count)))
  }

}
