/*
 * Copyright 2012 Twitter Inc.
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
package com.twitter.zipkin.storage.cassandra

import com.twitter.zipkin.common.{Annotation, Endpoint, Span}
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.thriftscala
import org.scalatest.FunSuite

import scala.collection.mutable.ArrayBuffer

class SnappyCodecTest extends FunSuite {

  val thriftCodec = new ScroogeThriftCodec[thriftscala.Span](thriftscala.Span)
  val snappyCodec = new SnappyCodec(thriftCodec)
  test("compress and decompress") {
    val expected = Span(123, "boo", 456, None, List(
      new Annotation(1, "bah", Some(Endpoint(23567, 345, "service"))),
      new Annotation(2, thriftscala.Constants.CLIENT_SEND, Some(Endpoint(23567, 345, "service"))),
      new Annotation(3, thriftscala.Constants.CLIENT_RECV, Some(Endpoint(23567, 345, "service")))),
      ArrayBuffer())
    val actual = snappyCodec.decode(snappyCodec.encode(expected.toThrift)).toSpan

    assert(expected === actual)
  }

}
