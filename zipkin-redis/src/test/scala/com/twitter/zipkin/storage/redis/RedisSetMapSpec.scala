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

import com.twitter.util.Await._
import org.jboss.netty.buffer.ChannelBuffers

class RedisSetMapSpec extends RedisSpecification {
  val setMap = new RedisSetMap(_client, "prefix", None)
  val buf1 = ChannelBuffers.copiedBuffer("val1")
  val buf2 = ChannelBuffers.copiedBuffer("val2")
  val buf3 = ChannelBuffers.copiedBuffer("val3")

  test("add an item then get it out") {
    result(setMap.add("key", buf1))
    result(setMap.get("key")) should be (Set(buf1))
  }

  test("add many items and then get them out") {
    result(setMap.add("key", buf1))
    result(setMap.add("key", buf2))
    result(setMap.add("key", buf3))
    result(setMap.get("key")) should be (Set(buf1, buf2, buf3))
  }
}
