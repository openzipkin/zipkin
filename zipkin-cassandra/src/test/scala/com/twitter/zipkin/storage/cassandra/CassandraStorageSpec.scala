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

import com.twitter.zipkin.config.CassandraStorageConfig
import com.twitter.zipkin.gen
import com.twitter.cassie.tests.util.FakeCassandra
import com.twitter.conversions.time._
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.util.Eval
import java.nio.ByteBuffer
import org.specs.mock.{ClassMocker, JMocker}
import org.specs.Specification
import com.twitter.io.TempFile
import com.twitter.zipkin.common.{BinaryAnnotation, Endpoint, Annotation, Span}
import com.twitter.zipkin.query.Trace

class CassandraStorageSpec extends Specification with JMocker with ClassMocker {
  object FakeServer extends FakeCassandra

  var cassandraStorage: CassandraStorage = null

  def binaryAnnotation(key: String, value: String) =
    BinaryAnnotation(key, ByteBuffer.wrap(value.getBytes), ThriftAdapter(gen.AnnotationType.String), Some(ep))

  val ep = Endpoint(123, 123, "service")

  val spanId = 456
  val ann1 = Annotation(1, "cs", Some(ep))
  val ann2 = Annotation(2, "sr", None)
  val ann3 = Annotation(2, "custom", Some(ep))
  val ann4 = Annotation(2, "custom", Some(ep))

  val span1 = Span(123, "methodcall", spanId, None, List(ann1, ann3),
    List(binaryAnnotation("BAH", "BEH")))

  "CassandraStorage" should {
    doBefore {
      FakeServer.start()
      val test = TempFile.fromResourcePath("/CassandraStorageConfig.scala")
      val env = RuntimeEnvironment(this, Array("-f", test.toString))
      val config = new Eval().apply[CassandraStorageConfig](env.configFile)
      config.cassandraConfig.port = FakeServer.port.get
      cassandraStorage = config.apply()
    }

    doAfter {
      cassandraStorage.close()
      FakeServer.stop()
    }

    "getSpansByTraceId" in {
      cassandraStorage.storeSpan(span1)()
      val spans = cassandraStorage.getSpansByTraceId(span1.traceId)()
      spans.isEmpty mustEqual false
      spans(0) mustEqual span1
    }

    "getSpansByTraceIds" in {
      cassandraStorage.storeSpan(span1)()
      val actual1 = cassandraStorage.getSpansByTraceIds(List(span1.traceId))()
      actual1.isEmpty mustEqual false

      val trace1 = Trace(actual1(0))
      trace1.spans.isEmpty mustEqual false
      trace1.spans(0) mustEqual span1

      val span2 = Span(666, "methodcall2", spanId, None, List(ann2),
        List(binaryAnnotation("BAH2", "BEH2")))
      cassandraStorage.storeSpan(span2)()
      val actual2 = cassandraStorage.getSpansByTraceIds(List(span1.traceId, span2.traceId))()
      actual2.isEmpty mustEqual false

      val trace2 = Trace(actual2(0))
      val trace3 = Trace(actual2(1))
      trace2.spans.isEmpty mustEqual false
      trace2.spans(0) mustEqual span1
      trace3.spans.isEmpty mustEqual false
      trace3.spans(0) mustEqual span2
    }

    "getSpansByTraceIds should return empty list if no trace exists" in {
      val actual1 = cassandraStorage.getSpansByTraceIds(List(span1.traceId))()
      actual1.isEmpty mustEqual true
    }

    "set time to live on a trace and then get it" in {
      cassandraStorage.storeSpan(span1)()
      cassandraStorage.setTimeToLive(span1.traceId, 1234.seconds)()
      cassandraStorage.getTimeToLive(span1.traceId)() mustEqual 1234.seconds
    }
  }
}
