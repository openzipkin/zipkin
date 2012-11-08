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

import org.jboss.netty.buffer.ChannelBuffer
import com.twitter.finagle.redis.Client
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import java.nio.charset.Charset
import com.twitter.util.Duration
import com.twitter.conversions.time.longToTimeableNumber

class RedisArrayMap(database: Client, prefix: String, defaultTTL: Option[Duration]) {

  def preface(key: String): String = "%s:%s".format(prefix, key)

  def get(key: String): Future[Seq[ChannelBuffer]] =
    database.lRange(preface(key), 0, -1)

  def delete(key: String): Future[Long] = database.del(Seq(preface(key))) map (_.longValue)

  def remove(key: String, members: Seq[ChannelBuffer]): Future[Unit] = {
    Future.join(members map { buffer =>
      database.lRem(preface(key), 1, buffer)
    })
  }

  def exists(key: String): Future[Boolean] = database.exists(preface(key)) map (_.booleanValue)

  def put(key: String, value: ChannelBuffer): Future[Unit] = {
    val string = preface(key)
    Future.join(Seq(database.lPush(string, List(value)),
    defaultTTL match {
      case Some(ttl) => database.expire(string, ttl.inSeconds)
      case None => Future.Unit
    }))
  }

  def setTTL(key: String, ttl: Duration): Future[Boolean] =
    database.expire(preface(key), ttl.inSeconds) map (_.booleanValue)

  def getTTL(key: String): Future[Option[Duration]] =
    database.ttl(preface(key)) map (opt => opt map (_.longValue.seconds))
}
