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

import org.specs.Specification
import com.twitter.zipkin.gen
import com.twitter.zipkin.common.{Span, Endpoint, Annotation}
import collection.mutable.ArrayBuffer

class SnappyCodecSpec extends Specification {

  val thriftCodec = new ScroogeThriftCodec[gen.Span](gen.Span)
  val snappyCodec = new SnappyCodec(thriftCodec)

  "SnappyCodec" should {

    "compress and decompress" in {
      val expected = Span(123, "boo", 456, None, List(
        new Annotation(1, "bah", Some(Endpoint(23567, 345, "service"))),
        new Annotation(2, gen.Constants.CLIENT_SEND, Some(Endpoint(23567, 345, "service"))),
        new Annotation(3, gen.Constants.CLIENT_RECV, Some(Endpoint(23567, 345, "service")))),
        ArrayBuffer())
      val actual = ThriftAdapter(snappyCodec.decode(snappyCodec.encode(ThriftAdapter(expected))))

      expected mustEqual actual
    }

  }
}