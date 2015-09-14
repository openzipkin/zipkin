package com.twitter.zipkin.storage.redis

import scala.collection.immutable.List

import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.zipkin.thriftscala
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.common.{Annotation, BinaryAnnotation, Span}


/**
 * Tests the RedisSnappyThriftCodec
 */
class RedisSnappyThriftCodecSpec extends RedisSpecification {

  val thriftCodec = new RedisSnappyThriftCodec(new BinaryThriftStructSerializer[thriftscala.Span] {
    override def codec = thriftscala.Span
  })

  val span = Span(1L, "name", 2L, Option(3L), List[Annotation](),
                  Seq[BinaryAnnotation](), true).toThrift

  test("compress and decompress should yield an equal object") {
    val bytes = thriftCodec.encode(span)
    val actualOutput = thriftCodec.decode(bytes)
    assertResult (span) (actualOutput)

  }
}
