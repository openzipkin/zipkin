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

import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.zipkin.adapter.ThriftAdapter
import com.twitter.zipkin.common.{Annotation, Span}
import com.twitter.zipkin.gen
import org.specs.Specification

class ScribeProcessorFilterSpec extends Specification {
  val serializer = new BinaryThriftStructSerializer[gen.Span] {
    def codec = gen.Span
  }

  "ScribeProcessorFilter" should {
    val category = "zipkin"

    val validSpan = Span(123, "boo", 456, None, List(new Annotation(1, "bah", None)), Nil)
    val base64 = serializer.toString(ThriftAdapter(validSpan))
    val logEntries = Seq(gen.LogEntry(category, base64))

    val badLogEntries = Seq(gen.LogEntry(category, "garbage!"))
    val filter = new ScribeProcessorFilter

    "convert gen.LogEntry to Span" in {
      filter.apply(logEntries) mustEqual Seq(validSpan)
    }

    "deal with garbage" in {
      filter.apply(badLogEntries) mustEqual Seq.empty[Span]
    }
  }
}
