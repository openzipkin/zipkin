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
package com.twitter.zipkin.collector.processor

import com.twitter.finagle.Service
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.zipkin.common.{Annotation, Span}
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.thriftscala
import org.specs.Specification
import org.specs.mock.{JMocker, ClassMocker}

class ScribeFilterSpec extends Specification with JMocker with ClassMocker {
  val serializer = new BinaryThriftStructSerializer[thriftscala.Span] {
    def codec = thriftscala.Span
  }

  val mockService = mock[Service[Span, Unit]]

  "ScribeFilter" should {
    val category = "zipkin"

    val base64 = Seq("CgABAAAAAAAAAHsLAAMAAAADYm9vCgAEAAAAAAAAAcgPAAYMAAAAAQoAAQAAAAAAAAABCwACAAAAA2JhaAAPAAgMAAAAAAA=")
    val endline = Seq("CgABAAAAAAAAAHsLAAMAAAADYm9vCgAEAAAAAAAAAcgPAAYMAAAAAQoAAQAAAAAAAAABCwACAAAAA2JhaAAPAAgMAAAAAAA=\n")

    val validSpan = Span(123, "boo", 456, None, List(new Annotation(1, "bah", None)), Nil)
    val serialized = Seq(serializer.toString(validSpan.toThrift))
    val bad = Seq("garbage!")

    val filter = new ScribeFilter

    "convert thriftscala.LogEntry to Span" in {
      expect {
        one(mockService).apply(validSpan)
      }
      filter.apply(base64, mockService)
    }

    "convert thriftscala.LogEntry with endline to Span" in {
      expect {
        one(mockService).apply(validSpan)
      }
      filter.apply(endline, mockService)
    }

    "convert serialized thrift to Span" in {
      expect {
        one(mockService).apply(validSpan)
      }
      filter.apply(serialized, mockService)
    }

    "deal with garbage" in {
      expect {}
      filter.apply(bad, mockService)
    }
  }
}
