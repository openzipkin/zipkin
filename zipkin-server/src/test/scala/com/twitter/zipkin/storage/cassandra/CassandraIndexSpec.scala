package com.twitter.zipkin.storage.cassandra

/*
 * Copyright 2012 Twitter Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
import scala.collection._
import java.nio.ByteBuffer
import org.specs.Specification
import org.specs.mock.{JMocker, ClassMocker}
import com.twitter.conversions.time._
import scala.collection.JavaConverters._

import com.twitter.zipkin.gen
import com.twitter.cassie.tests.util.FakeCassandra
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.util.{Eval, Future}
import java.util.{Set => JSet}
import com.twitter.cassie.{BatchMutationBuilder, Column, Order, ColumnFamily}
import com.twitter.zipkin.config.{CassandraConfig, CassandraIndexConfig}
import com.twitter.io.TempFile
import com.twitter.zipkin.common.{BinaryAnnotation, Annotation, Span, Endpoint}
import com.twitter.zipkin.adapter.ThriftAdapter

class CassandraIndexSpec extends Specification with JMocker with ClassMocker {
  object FakeServer extends FakeCassandra
  var cassandraIndex: CassandraIndex = null

  val ep = Endpoint(123, 123, "service")

  def binaryAnnotation(key: String, value: String) =
    BinaryAnnotation(key, ByteBuffer.wrap(value.getBytes), ThriftAdapter(gen.AnnotationType.String), Some(ep))

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

  "CassandraIndex" should {
    doBefore {
      FakeServer.start()
      val test = TempFile.fromResourcePath("/CassandraIndexConfig.scala")
      val env = RuntimeEnvironment(this, Array("-f", test.toString))
      val config = new Eval().apply[CassandraIndexConfig](env.configFile)
      config.cassandraConfig.port = FakeServer.port.get
      cassandraIndex = config.apply()
    }

    doAfter {
      cassandraIndex.close()
      FakeServer.stop()
    }

    "index and get span names" in {
      cassandraIndex.indexSpanNameByService(span1)()
      cassandraIndex.getSpanNames("service")() mustEqual Set(span1.name)
    }

    "index and get service names" in {
      cassandraIndex.indexServiceName(span1)()
      cassandraIndex.getServiceNames() mustEqual Set(span1.serviceNames.head)
    }

    "index only on annotation in each span with the same value" in {
      val _annotationsIndex = mock[ColumnFamily[ByteBuffer, Long, Long]]
      val batch = mock[BatchMutationBuilder[ByteBuffer, Long, Long]]
      val _config = mock[CassandraConfig]

      val cs = new CassandraIndex() {
        val config = _config
        val serviceSpanNameIndex = null
        val serviceNameIndex = null
        val annotationsIndex = _annotationsIndex
        val durationIndex = null
        val serviceNames = null
        val spanNames = null
      }
      val col = Column[Long, Long](ann3.timestamp, span3.traceId)

      expect {
        2.of(_config).tracesTimeToLive willReturn 20.days

        one(_annotationsIndex).batch willReturn batch
        one(batch).insert(a[ByteBuffer], a[Column[Long, Long]])
        allowingMatch(batch, "insert")
        one(batch).execute
      }

      cs.indexSpanByAnnotations(span3)
    }

    "getTraceIdsByName" in {
      var ls = List[Long]()
      //cassandra.storeSpan(span1)()
      cassandraIndex.indexTraceIdByServiceAndName(span1)()
      cassandraIndex.getTraceIdsByName("service", None, 0, 3)() foreach {
        _ mustEqual span1.traceId
      }
      cassandraIndex.getTraceIdsByName("service", Some("methodname"), 0, 3)() foreach {
        _ mustEqual span1.traceId
      }
    }

    "getTracesDuration" in {
      // no support in FakeCassandra for order and limit and it seems tricky to add
      // so will mock the index instead

      val _durationIndex = new ColumnFamily[Long, Long, String] {
        override def multigetRows(keys: JSet[Long], startColumnName: Option[Long], endColumnName: Option[Long], order: Order, count: Int) = {
          if (!order.reversed) {
            Future.value(Map(321L -> Map(100L -> Column(100L, "")).asJava).asJava)
          } else {
            Future.value(Map(321L -> Map(120L -> Column(120L, "")).asJava).asJava)
          }
        }
      }

      val cass = new CassandraIndex() {
        val config = new CassandraConfig{}
        val serviceSpanNameIndex = null
        val serviceNameIndex = null
        val annotationsIndex = null
        val durationIndex = _durationIndex
        val serviceNames = null
        val spanNames = null
      }

      val duration = cass.getTracesDuration(Seq(321L))()
      duration(0).traceId mustEqual 321L
      duration(0).duration mustEqual 20
    }

    "get no trace durations due to missing data" in {
      // no support in FakeCassandra for order and limit and it seems tricky to add
      // so will mock the index instead

      val _durationIndex = new ColumnFamily[Long, Long, String] {
        override def multigetRows(keys: JSet[Long], startColumnName: Option[Long], endColumnName: Option[Long], order: Order, count: Int) = {
          if (!order.reversed) {
            Future.value(Map(321L -> Map(100L -> Column(100L, "")).asJava).asJava)
          } else {
            Future.value(Map(321L -> Map[Long, Column[Long,String]]().asJava).asJava)
          }
        }
      }

      val cass = new CassandraIndex() {
        val config = new CassandraConfig{}
        val serviceSpanNameIndex = null
        val serviceNameIndex = null
        val annotationsIndex = null
        val durationIndex = _durationIndex
        val serviceNames = null
        val spanNames = null
      }

      val duration = cass.getTracesDuration(Seq(321L))()
      duration.isEmpty mustEqual true
    }

    "getTraceIdsByAnnotation" in {
      //cassandra.storeSpan(span1)()
      cassandraIndex.indexSpanByAnnotations(span1)()

      // fetch by time based annotation, find trace
      var seq = cassandraIndex.getTraceIdsByAnnotation("service", "custom", None, 0, 3)()
      //seq mustEqual Seq(span1.traceId)

      // should not find any traces since the core annotation doesn't exist in index
      seq = cassandraIndex.getTraceIdsByAnnotation("service", "cs", None, 0, 3)()
      seq.isEmpty mustBe true

      // should find traces by the key and value annotation
      seq = cassandraIndex.getTraceIdsByAnnotation("service", "BAH",
        Some(ByteBuffer.wrap("BEH".getBytes)), 0, 3)()
      seq mustEqual Seq(span1.traceId)
    }

    "not index empty service name" in {
      cassandraIndex.indexServiceName(spanEmptyServiceName)
      val serviceNames = cassandraIndex.getServiceNames()
      serviceNames.isEmpty mustBe true
    }

    "not index empty span name " in {
      cassandraIndex.indexSpanNameByService(spanEmptySpanName)
      val spanNames = cassandraIndex.getSpanNames(spanEmptySpanName.name)
      spanNames().isEmpty mustBe true
    }
  }
}
