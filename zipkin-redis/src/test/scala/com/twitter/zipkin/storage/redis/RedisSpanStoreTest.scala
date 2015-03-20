/*
 * Copyright 2014 Twitter Inc.
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

import com.twitter.app.App
import com.twitter.zipkin.redis.RedisSpanStoreFactory
import com.twitter.zipkin.storage.util.SpanStoreValidator
import org.junit.runner.RunWith
import com.twitter.util.Await
import com.twitter.app.App
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RedisSpanStoreTest extends FunSuite {

  object RedisStore extends App with RedisSpanStoreFactory
  RedisStore.main(Array(
    "-zipkin.storage.redis.host", "127.0.0.1",
    "-zipkin.storage.redis.port", "6379"))

  def newSpanStore = {
    val spanStore = RedisStore.newRedisSpanStore()
    Await.result(spanStore.storage.database.flushDB())
    spanStore
  }

  test("validate") {
    new SpanStoreValidator(newSpanStore).validate
  }
}
