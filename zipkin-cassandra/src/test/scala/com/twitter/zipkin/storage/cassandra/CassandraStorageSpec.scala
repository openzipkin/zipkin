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

import com.twitter.cassie.tests.util.FakeCassandra
import com.twitter.conversions.time._
import java.nio.ByteBuffer
import org.specs.mock.{ClassMocker, JMocker}
import org.specs.Specification
import com.twitter.zipkin.common._
import com.twitter.zipkin.query.Trace
import com.twitter.zipkin.cassandra.{Keyspace, StorageBuilder}
import com.twitter.util.Await

class CassandraStorageSpec extends Specification with JMocker with ClassMocker {
  object FakeServer extends FakeCassandra

  var cassandraStorage: CassandraStorage = null

  def binaryAnnotation(key: String, value: String) =
    BinaryAnnotation(key, ByteBuffer.wrap(value.getBytes), AnnotationType.String, Some(ep))

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
      val keyspaceBuilder = Keyspace.static(port = FakeServer.port.get)
      val builder = StorageBuilder(keyspaceBuilder)
      cassandraStorage = builder.apply()
    }

    doAfter {
      cassandraStorage.close()
      FakeServer.stop()
    }

    "getSpansByTraceId" in {
      Await.result(cassandraStorage.storeSpan(span1))
      val spans = Await.result(cassandraStorage.getSpansByTraceId(span1.traceId))
      spans.isEmpty mustEqual false
      spans(0) mustEqual span1
    }

    "getSpansByTraceIds" in {
      Await.result(cassandraStorage.storeSpan(span1))
      val actual1 = Await.result(cassandraStorage.getSpansByTraceIds(List(span1.traceId)))
      actual1.isEmpty mustEqual false

      val trace1 = Trace(actual1(0))
      trace1.spans.isEmpty mustEqual false
      trace1.spans(0) mustEqual span1

      val span2 = Span(666, "methodcall2", spanId, None, List(ann2),
        List(binaryAnnotation("BAH2", "BEH2")))
      Await.result(cassandraStorage.storeSpan(span2))
      val actual2 = Await.result(cassandraStorage.getSpansByTraceIds(List(span1.traceId, span2.traceId)))
      actual2.isEmpty mustEqual false

      val trace2 = Trace(actual2(0))
      val trace3 = Trace(actual2(1))
      trace2.spans.isEmpty mustEqual false
      trace2.spans(0) mustEqual span1
      trace3.spans.isEmpty mustEqual false
      trace3.spans(0) mustEqual span2
    }

    "getSpansByTraceIds should return empty list if no trace exists" in {
      val actual1 = Await.result(cassandraStorage.getSpansByTraceIds(List(span1.traceId)))
      actual1.isEmpty mustEqual true
    }

    "all binary annotations are logged" in {
      val a_traceId = 1234L
      val a1 = Annotation(1, "sr", Some(ep))
      val a2 = Annotation(2, "ss", Some(ep))
      val ba1 = binaryAnnotation("key1", "value1")
      val ba2 = binaryAnnotation("key2", "value2")
      val originalKeyNames = Set("key1", "key2")
      val a_span1 = Span(a_traceId, "test", 345L, None, List(a1), Nil)
      val a_span2 = Span(a_traceId, "test", 345L, None, Nil, List(ba1))
      val a_span3 = Span(a_traceId, "test", 345L, None, Nil, List(ba2))
      val a_span4 = Span(a_traceId, "test", 345L, None, List(a2), Nil)
      val data = List(a_span1, a_span2, a_span3, a_span4)
      for(s <- data) {
        Await.result(cassandraStorage.storeSpan(s))
      }

      val actual1 = Await.result(cassandraStorage.getSpansByTraceIds(List(a_traceId)))
      val trace1 = Trace(actual1(0))
      val bAnnotations = trace1.spans(0).binaryAnnotations
      val keyNames = bAnnotations map { _.key }
      bAnnotations.length mustEqual 2
      keyNames.toSet mustEqual originalKeyNames

    }

    "set time to live on a trace and then get it" in {
      Await.result(cassandraStorage.storeSpan(span1))
      Await.result(cassandraStorage.setTimeToLive(span1.traceId, 1234.seconds))
      Await.result(cassandraStorage.getTimeToLive(span1.traceId)) mustEqual 1234.seconds
    }
  }
}
