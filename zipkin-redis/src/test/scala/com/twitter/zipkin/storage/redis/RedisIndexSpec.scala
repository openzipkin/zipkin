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
import com.twitter.util.Await.result
import com.twitter.zipkin.common.{Annotation, AnnotationType, BinaryAnnotation, Endpoint, Span}
import com.twitter.zipkin.storage.IndexedTraceId

class RedisIndexSpec extends RedisSpecification {
  val redisIndex = new RedisIndex {
    val database = _client
    val ttl = Some(7.days)
  }

  val ep = Endpoint(123, 123, "service")

  def binaryAnnotation(key: String, value: String) =
    BinaryAnnotation(
      key,
      ByteBuffer.wrap(value.getBytes),
      AnnotationType.String,
      Some(ep)
    )

  val spanId = 456
  val ann1 = Annotation(1, "cs", Some(ep))
  val ann2 = Annotation(2, "sr", None)
  val ann3 = Annotation(2, "custom", Some(ep))
  val ann4 = Annotation(2, "custom", Some(ep))

  val span1 = Span(123, "methodcall", spanId, None, List(ann1, ann3),
    List(binaryAnnotation("BAH", "BEH")))
  val span2 = Span(123, "methodcall", spanId, None, List(ann2),
    List(binaryAnnotation("BAH2", "BEH2")))
  val span3 = Span(123, "methodcall", spanId, None, List(ann2, ann3, ann4),
    List(binaryAnnotation("BAH2", "BEH2")))

  val spanEmptySpanName = Span(123, "", spanId, None, List(ann1, ann2), List())
  val spanEmptyServiceName = Span(123, "spanname", spanId, None, List(), List())

  val mergedSpan = Span(123, "methodcall", spanId, None,
    List(ann1, ann2), List(binaryAnnotation("BAH2", "BEH2")))

  test("index and get span names") {
    result(redisIndex.indexSpanNameByService(span1))
    result(redisIndex.getSpanNames("service")) should be (Set(span1.name))
  }

  test("index and get service names") {
    result(redisIndex.indexServiceName(span1))
    result(redisIndex.getServiceNames) should be (Set(span1.serviceNames.head))
  }

  test("index only on annotation in each span with the same value") {
    result(redisIndex.indexSpanByAnnotations(span3))
  }

  test("getTraceIdsByName") {
    var ls = List[Long]()
    result(redisIndex.indexTraceIdByServiceAndName(span1))
    result(redisIndex.getTraceIdsByName("service", None, 0, 3)) foreach {
      _ should be (span1.traceId)
    }
    result(redisIndex.getTraceIdsByName("service", Some("methodname"), 0, 3)) foreach {
      _ should be (span1.traceId)
    }
  }

  test("getTraceIdsByAnnotation") {
    result(redisIndex.indexSpanByAnnotations(span1))

    // fetch by time based annotation, find trace
    var seq = result(redisIndex.getTraceIdsByAnnotation("service", "custom", None, 3, 3))
    (seq map (_.traceId)) should be (Seq(span1.traceId))

    // should not find any traces since the core annotation doesn't exist in index
    seq = result(redisIndex.getTraceIdsByAnnotation("service", "cs", None, 0, 3))
    //seq.isEmpty should be (true)

    // should find traces by the key and value annotation
    seq = result(redisIndex.getTraceIdsByAnnotation("service", "BAH",
      Some(ByteBuffer.wrap("BEH".getBytes)), 4, 3))
    seq should be (Seq(IndexedTraceId(span1.traceId, span1.lastAnnotation.get.timestamp)))
  }

  test("not index empty service name") {
    result(redisIndex.indexServiceName(spanEmptyServiceName))
    val serviceNames = result(redisIndex.getServiceNames)
    serviceNames.isEmpty should be (true)
  }

  test("not index empty span name ") {
    result(redisIndex.indexSpanNameByService(spanEmptySpanName))
    val spanNames = result(redisIndex.getSpanNames(spanEmptySpanName.name))
    spanNames.isEmpty should be (true)
  }
}
