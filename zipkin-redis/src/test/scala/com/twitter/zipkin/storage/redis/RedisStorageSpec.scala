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

import java.nio.ByteBuffer

import com.twitter.conversions.time.intToTimeableNumber
import com.twitter.zipkin.common.{Annotation, AnnotationType, BinaryAnnotation, Endpoint, Span}

class RedisStorageSpec extends RedisSpecification {

  var redisStorage = new RedisStorage {
    val database = _client
    val ttl = Some(7.days)
  }

  def binaryAnnotation(key: String, value: String) =
    BinaryAnnotation(
      key,
      ByteBuffer.wrap(value.getBytes),
      AnnotationType.String,
      Some(ep)
    )

  val ep = Endpoint(123, 123, "service")

  val spanId = 456
  val ann1 = Annotation(1, "cs", Some(ep))
  val ann2 = Annotation(2, "sr", None)
  val ann3 = Annotation(2, "custom", Some(ep))
  val ann4 = Annotation(2, "custom", Some(ep))

  val span1 = Span(123, "methodcall", spanId, None, List(ann1, ann3),
    List(binaryAnnotation("BAH", "BEH")))

  test("getTraceById") {
    redisStorage.storeSpan(span1)()
    val trace = redisStorage.getSpansByTraceId(span1.traceId)()
    trace.isEmpty should be (false)
    trace(0) should be (span1)
  }

  test("getTracesByIds") {
    redisStorage.storeSpan(span1)()
    val actual1 = redisStorage.getSpansByTraceIds(List(span1.traceId))()
    actual1.isEmpty should be (false)
    actual1(0).isEmpty should be (false)
    actual1(0)(0) should be (span1)

    val span2 = Span(666, "methodcall2", spanId, None, List(ann2),
      List(binaryAnnotation("BAH2", "BEH2")))
    redisStorage.storeSpan(span2)()
    val actual2 = redisStorage.getSpansByTraceIds(List(span1.traceId, span2.traceId))()
    actual2.isEmpty should be (false)
    actual2(0).isEmpty should be (false)
    actual2(0)(0) should be (span1)
    actual2(1).isEmpty should be (false)
    actual2(1)(0) should be (span2)
  }

  test("getTracesByIds should return empty list if no trace exists") {
    val actual1 = redisStorage.getSpansByTraceIds(List(span1.traceId))()
    actual1.isEmpty should be (true)
  }

  test("set time to live on a trace and then get it") {
    redisStorage.storeSpan(span1)()
    redisStorage.setTimeToLive(span1.traceId, 1234.seconds)()
    redisStorage.getTimeToLive(span1.traceId)() should be (1234.seconds)
  }
}
