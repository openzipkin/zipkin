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

class RedisArrayMapSpec extends RedisSpecification {
  "RedisArrayMapSpec" should {
    var arrayMap: RedisArrayMap = null
    val buf1 = ChannelBuffers.copiedBuffer("value1")
    val buf2 = ChannelBuffers.copiedBuffer("value2")
    val buf3 = ChannelBuffers.copiedBuffer("value3")

    doBefore {
      _client.flushDB()
      arrayMap= new RedisArrayMap(_client, "prefix", None)
    }

    "be able to insert an element properly" in {
      arrayMap.put("key", buf1)()
      arrayMap.get("key")() mustEqual Seq(buf1)
    }

    "be able to insert a few elements properly" in {
      arrayMap.put("key", buf2)()
      arrayMap.put("key", buf3)()
      arrayMap.put("key", buf1)()
      arrayMap.get("key")().toSet mustEqual Set(buf1, buf2, buf3)
    }

    "be able to remove an element properly" in {
      arrayMap.put("key", buf2)()
      arrayMap.put("key", buf3)()
      arrayMap.put("key", buf1)()
      arrayMap.remove("key", Seq(buf1))()
      arrayMap.get("key")().toSet mustEqual Set(buf2, buf3)
    }

    "be able to remove a few elements properly" in {
      arrayMap.put("key", buf2)()
      arrayMap.put("key", buf3)()
      arrayMap.put("key", buf1)()
      arrayMap.remove("key", Seq(buf1, buf2))()
      arrayMap.get("key")().toSet mustEqual Set(buf3)
    }

    "be able to obliterate a key (and check that it is in fact obliterated)" in {
      arrayMap.put("key", buf2)()
      arrayMap.put("key", buf3)()
      arrayMap.put("key", buf1)()
      arrayMap.exists("key")() mustEqual true
      arrayMap.delete("key")()
      arrayMap.get("key")() mustEqual Seq()
      arrayMap.exists("key")() mustEqual false
    }

    "array map should get timeout properly" in {
      val tmp = new RedisArrayMap(_client, "timing", Some(1000.seconds))
      tmp.put("key", buf1)()
      tmp.getTTL("key")() mustEqual(Some(1000.seconds))
    }

    "array map should get invalid timeout properly" in {
      arrayMap.put("key", buf1)()
      arrayMap.getTTL("key")() mustEqual(None)
    }

    "array map should timeout properly" in {
      val tmp = new RedisArrayMap(_client, "timing", Some(5.seconds))
      tmp.put("key", buf1)()
      Thread.sleep(10000)
      tmp.get("key")() mustEqual Seq.empty
    }

    "array map should be able to reset timeout properly" in {
      arrayMap.put("key", buf1)()
      arrayMap.setTTL("key", 1000.seconds)() mustEqual(true)
      arrayMap.getTTL("key")() mustEqual(Some(1000.seconds))
    }
  }
}