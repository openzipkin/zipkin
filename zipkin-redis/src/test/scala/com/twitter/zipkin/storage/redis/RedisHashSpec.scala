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

import org.jboss.netty.buffer.ChannelBuffers

class RedisHashSpec extends RedisSpecification {
  val hash = new RedisHash(_client, "prefix")
  val key1 = ChannelBuffers.copiedBuffer("key1")
  val key2 = ChannelBuffers.copiedBuffer("key2")
  val key3 = ChannelBuffers.copiedBuffer("key3")
  val buf1 = ChannelBuffers.copiedBuffer("buf1")
  val buf2 = ChannelBuffers.copiedBuffer("buf2")
  val buf3 = ChannelBuffers.copiedBuffer("buf3")

  val buf4 = ChannelBuffers.buffer(8)
  buf4.writeLong(100L)

  test("place a new item and get it out") {
    hash.put(key1, buf1)() should be (1)
    hash.get(key1)() should be (Some(buf1))
  }

  test("place a new item and update it") {
    hash.put(key1, buf1)() should be (1)
    hash.put(key1, buf2)() should be (0)
    hash.get(key1)() should be (Some(buf2))
  }

  test("place a few items and get them out") {
    hash.put(key1, buf1)() should be (1)
    hash.put(key2, buf2)() should be (1)
    hash.put(key3, buf3)() should be (1)
    hash.get(key1)() should be (Some(buf1))
    hash.get(key2)() should be (Some(buf2))
    hash.get(key3)() should be (Some(buf3))
  }

  test("place a few items and remove some") {
    hash.put(key1, buf1)() should be (1)
    hash.put(key2, buf2)() should be (1)
    hash.put(key3, buf3)() should be (1)
    hash.remove(Seq(key1, key2))() should be (2)
    hash.get(key1)() should be (None)
    hash.get(key2)() should be (None)
    hash.get(key3)() should be (Some(buf3))
  }

  test("place an item and incr it") {
    val buf5 = ChannelBuffers.buffer(8)
    buf5.writeLong(101L)
    hash.put(key1, buf4)() should be (1)
    hash.incrBy(key1, 1)() should be (101)
    hash.get(key1)() should be (Some(buf5))
  }
}
