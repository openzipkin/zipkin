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
import com.twitter.util.Future
import java.nio.ByteBuffer
import org.jboss.netty.buffer.ChannelBuffer

class RedisHash(database: Client, name: String) {

  def put(key: ChannelBuffer, value: ChannelBuffer) = {
    database.hSet(name, key, value)
  }

  def get(key: ChannelBuffer): Future[Option[ChannelBuffer]] =
    database.hGet(name, key)

  def incrBy(key: ChannelBuffer, incrValue: Long): Future[Long] = database.hGet(name, key) map {
    _ match {
      case Some(curValue) => {
        val sum = incrValue + chanBuf2Long(curValue)
        database.hSet(name, key, sum)
        sum
      }
      case None => {
        database.hSet(name, key, incrValue)
        incrValue
      }
    }
  }

  def remove(keys: Seq[ChannelBuffer]): Future[Long] = {
    database.hDel(name, keys) map (_.longValue)
  }
}
