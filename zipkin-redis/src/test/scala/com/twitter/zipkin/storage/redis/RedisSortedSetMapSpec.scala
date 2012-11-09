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

import com.twitter.finagle.redis.protocol.ZRangeResults
import com.twitter.zipkin.common.{Annotation, BinaryAnnotation, Endpoint, Span}
import com.twitter.zipkin.conversions.thrift.thriftAnnotationTypeToAnnotationType
import com.twitter.zipkin.gen
import java.nio.ByteBuffer
import java.nio.charset.Charset
import org.jboss.netty.buffer.ChannelBuffers
import scala.util.Random

class RedisSortedSetMapSpec extends RedisSpecification {
  val ep = Endpoint(123, 123, "service")

  def binaryAnnotation(key: String, value: String) =
    BinaryAnnotation(
      key,
      ByteBuffer.wrap(value.getBytes),
      gen.AnnotationType.String.toAnnotationType,
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

  "RedisSortedSetMap" should {
    var setMap: RedisSortedSetMap = null

    implicit def z2seq(z: ZRangeResults): Seq[Pair[String, Double]] = z.asTuples map {
      case (buf, double) => chanBuf2String(buf) -> double
    }

    implicit def z2longs(z: ZRangeResults): Seq[Pair[Long, Double]] = z.asTuples map {
      case (buf, double) => chanBuf2Long(buf) -> double
    }

    doBefore {
      val rand = new Random
      val random = rand.nextString(6)
      setMap = new RedisSortedSetMap(_client, random, None)
      val database = _client
    }

    "put a value in and get it out" in {
      setMap.add("key", 0.0, ChannelBuffers.copiedBuffer("whatever", Charset.defaultCharset))
      z2seq(setMap.get("key", -1, 1, 1)()) mustEqual Seq("whatever" -> 0.0)
    }

    "follow the workflow for an index" in {
      val span = span1
      val time = span.lastAnnotation.get.timestamp
      var seq: String = null
      val binaryAnnos = for (serviceName <- span.serviceNames;
        binaryAnno <- span.binaryAnnotations)
        setMap.add(Seq(serviceName, binaryAnno.key, chanBuf2String(ChannelBuffers.copiedBuffer(binaryAnno.value))) mkString ":", time, span.traceId)
      z2longs(setMap.get(Seq("service", "BAH", "BEH") mkString ":", 0, 4, 3)()) mustEqual Seq(span1.traceId -> time.toDouble)
    }
  }
}
