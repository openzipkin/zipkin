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

import com.twitter.conversions.time.intToTimeableNumber
import org.jboss.netty.buffer.ChannelBuffers

class RedisListMapSpec extends RedisSpecification {
  val listMap: RedisListMap = new RedisListMap(_client, "prefix", None)
  val buf1 = ChannelBuffers.copiedBuffer("value1")
  val buf2 = ChannelBuffers.copiedBuffer("value2")
  val buf3 = ChannelBuffers.copiedBuffer("value3")

  test("insert an element properly") {
    listMap.put("key", buf1)()
    listMap.get("key")() should be (Seq(buf1))
  }

  test("insert a few elements properly") {
    listMap.put("key", buf2)()
    listMap.put("key", buf3)()
    listMap.put("key", buf1)()
    listMap.get("key")().toSet should be (Set(buf1, buf2, buf3))
  }

  test("remove an element properly") {
    listMap.put("key", buf2)()
    listMap.put("key", buf3)()
    listMap.put("key", buf1)()
    listMap.remove("key", Seq(buf1))()
    listMap.get("key")().toSet should be (Set(buf2, buf3))
  }

  test("remove a few elements properly") {
    listMap.put("key", buf2)()
    listMap.put("key", buf3)()
    listMap.put("key", buf1)()
    listMap.remove("key", Seq(buf1, buf2))()
    listMap.get("key")().toSet should be (Set(buf3))
  }

  test("obliterate a key (and check that it is in fact obliterated)") {
    listMap.put("key", buf2)()
    listMap.put("key", buf3)()
    listMap.put("key", buf1)()
    listMap.exists("key")() should be (true)
    listMap.delete("key")()
    listMap.get("key")() should be (Seq())
    listMap.exists("key")() should be (false)
  }

  test("array map should get timeout properly") {
    val tmp = new RedisListMap(_client, "timing", Some(1000.seconds))
    tmp.put("key", buf1)()
    tmp.getTTL("key")() should be (Some(1000.seconds))
  }

  test("array map should get invalid timeout properly") {
    listMap.put("key", buf1)()
    listMap.getTTL("key")() should be (None)
  }

  test("array map should timeout properly") {
    val tmp = new RedisListMap(_client, "timing", Some(5.seconds))
    tmp.put("key", buf1)()
    Thread.sleep(10000)
    tmp.get("key")() should be (Seq.empty)
  }

  test("array map should reset timeout properly") {
    listMap.put("key", buf1)()
    listMap.setTTL("key", 1000.seconds)() should be (true)
    listMap.getTTL("key")() should be (Some(1000.seconds))
  }
}
