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
import com.twitter.zipkin.common.{Annotation, AnnotationType, BinaryAnnotation, Endpoint, Span}
import com.twitter.zipkin.conversions.thrift.thriftAnnotationTypeToAnnotationType
import com.twitter.zipkin.gen
import com.twitter.zipkin.storage.IndexedTraceId
import java.nio.ByteBuffer

class RedisIndexSpec extends RedisSpecification {
  var redisIndex: RedisIndex = null

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

  "RedisIndex" should {
    doBefore {
      _client.flushDB()
      redisIndex = new RedisIndex {
        val database = _client
        val ttl = Some(7.days)
      }
    }

    doAfter {
      redisIndex.close()
    }

    "index and get span names" in {
      redisIndex.indexSpanNameByService(span1)()
      redisIndex.getSpanNames("service")() mustEqual Set(span1.name)
    }

    "index and get service names" in {
      redisIndex.indexServiceName(span1)()
      redisIndex.getServiceNames() mustEqual Set(span1.serviceNames.head)
    }

    "index only on annotation in each span with the same value" in {
      redisIndex.indexSpanByAnnotations(span3)
    }

    "getTraceIdsByName" in {
      var ls = List[Long]()
      redisIndex.indexTraceIdByServiceAndName(span1)()
      redisIndex.getTraceIdsByName("service", None, 0, 3)() foreach {
        _ mustEqual span1.traceId
      }
      redisIndex.getTraceIdsByName("service", Some("methodname"), 0, 3)() foreach {
        _ mustEqual span1.traceId
      }
    }

    "getTraceIdsByAnnotation" in {
      redisIndex.indexSpanByAnnotations(span1)()

      // fetch by time based annotation, find trace
      var seq = redisIndex.getTraceIdsByAnnotation("service", "custom", None, 3, 3)()
      (seq map (_.traceId)) mustEqual Seq(span1.traceId)

      // should not find any traces since the core annotation doesn't exist in index
      seq = redisIndex.getTraceIdsByAnnotation("service", "cs", None, 0, 3)()
      //seq.isEmpty mustBe true

      // should find traces by the key and value annotation
      seq = redisIndex.getTraceIdsByAnnotation("service", "BAH",
        Some(ByteBuffer.wrap("BEH".getBytes)), 4, 3)()
      seq mustEqual Seq(IndexedTraceId(span1.traceId, span1.lastAnnotation.get.timestamp))
    }

    "not index empty service name" in {
      redisIndex.indexServiceName(spanEmptyServiceName)
      val serviceNames = redisIndex.getServiceNames()
      serviceNames.isEmpty mustBe true
    }

    "not index empty span name " in {
      redisIndex.indexSpanNameByService(spanEmptySpanName)
      val spanNames = redisIndex.getSpanNames(spanEmptySpanName.name)
      spanNames().isEmpty mustBe true
    }
  }
}
