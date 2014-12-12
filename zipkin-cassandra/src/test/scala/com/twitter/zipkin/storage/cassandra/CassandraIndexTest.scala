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
import com.twitter.cassie._
import com.twitter.cassie.tests.util.FakeCassandra
import com.twitter.util.Future
import com.twitter.zipkin.cassandra.{IndexBuilder, Keyspace}
import com.twitter.zipkin.common._
import java.nio.ByteBuffer
import java.util.{Set => JSet}
import org.junit.runner.RunWith
import org.mockito.Matchers._
import org.mockito.Mockito.{atLeastOnce, times, verify, when}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, FunSuite}
import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class CassandraIndexTest extends FunSuite with BeforeAndAfter with MockitoSugar {
  object FakeServer extends FakeCassandra

  val mockKeyspace = mock[Keyspace]
  var cassandraIndex: CassandraIndex = null

  val ep = Endpoint(123, 123, "service")

  def binaryAnnotation(key: String, value: String) =
    BinaryAnnotation(key, ByteBuffer.wrap(value.getBytes), AnnotationType.String, Some(ep))

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

  before {
    FakeServer.start()
    val keyspaceBuilder = Keyspace.static(port = FakeServer.port.get)
    val builder = IndexBuilder(keyspaceBuilder)
    cassandraIndex = builder.apply()
  }

  after {
    cassandraIndex.close()
    FakeServer.stop()
  }

  test("index and get span names") {
    cassandraIndex.indexSpanNameByService(span1)()
    assert(cassandraIndex.getSpanNames("service")() === Set(span1.name))
  }

  test("index and get service names") {
    cassandraIndex.indexServiceName(span1)()
    assert(cassandraIndex.getServiceNames() === Set(span1.serviceNames.head))
  }

  test("index only on annotation in each span with the same value") {
    val mockAnnotationsIndex = mock[ColumnFamily[ByteBuffer, Long, Long]]
    val batch = mock[BatchMutationBuilder[ByteBuffer, Long, Long]]

    val cs = new CassandraIndex(mockKeyspace, null, null, null, null, mockAnnotationsIndex, null)
    val col = Column[Long, Long](ann3.timestamp, span3.traceId)

    when(mockAnnotationsIndex.batch).thenReturn(batch)
    when(batch.execute).thenReturn(Future.Void)

    cs.indexSpanByAnnotations(span3)

    verify(batch, atLeastOnce()).insert(any[ByteBuffer], any[Column[Long, Long]])
    verify(batch, times(1)).execute()
  }

  test("getTraceIdsByName") {
    var ls = List[Long]()
    //cassandra.storeSpan(span1)()
    cassandraIndex.indexTraceIdByServiceAndName(span1)()
    cassandraIndex.getTraceIdsByName("service", None, 0, 3)() foreach { m =>
      assert(m.traceId === span1.traceId)
    }
    cassandraIndex.getTraceIdsByName("service", Some("methodname"), 0, 3)() foreach { m =>
      assert(m.traceId === span1.traceId)
    }
  }

  test("getTracesDuration") {
    // no support in FakeCassandra for order and limit and it seems tricky to add
    // so will mock the index instead

    val durationIndex = new ColumnFamily[Long, Long, String] {
      override def multigetRows(keys: JSet[Long], startColumnName: Option[Long], endColumnName: Option[Long], order: Order, count: Int) = {
        if (!order.reversed) {
          Future.value(Map(321L -> Map(100L -> Column(100L, "")).asJava).asJava)
        } else {
          Future.value(Map(321L -> Map(120L -> Column(120L, "")).asJava).asJava)
        }
      }
    }

    val cass = CassandraIndex(mockKeyspace, null, null, null, null, null, durationIndex)

    val duration = cass.getTracesDuration(Seq(321L))()
    assert(duration(0).traceId === 321L)
    assert(duration(0).duration === 20)
  }

  test("get no trace durations due to missing data") {
    // no support in FakeCassandra for order and limit and it seems tricky to add
    // so will mock the index instead

    val durationIndex = new ColumnFamily[Long, Long, String] {
      override def multigetRows(keys: JSet[Long], startColumnName: Option[Long], endColumnName: Option[Long], order: Order, count: Int) = {
        if (!order.reversed) {
          Future.value(Map(321L -> Map(100L -> Column(100L, "")).asJava).asJava)
        } else {
          Future.value(Map(321L -> Map[Long, Column[Long,String]]().asJava).asJava)
        }
      }
    }

    val cass = CassandraIndex(mockKeyspace, null, null, null, null, null, durationIndex)

    val duration = cass.getTracesDuration(Seq(321L))()
    assert(duration.isEmpty)
  }

  test("getTraceIdsByAnnotation") {
    cassandraIndex.indexSpanByAnnotations(span1)()

    // fetch by time based annotation, find trace
    val map1 = cassandraIndex.getTraceIdsByAnnotation("service", "custom", None, 0, 3)()
    map1.foreach { m =>
      assert(m.traceId === span1.traceId)
    }

    // should not find any traces since the core annotation doesn't exist in index
    val map2 = cassandraIndex.getTraceIdsByAnnotation("service", "cs", None, 0, 3)()
    assert(map2.isEmpty)

    // should find traces by the key and value annotation
    val map3 = cassandraIndex.getTraceIdsByAnnotation("service", "BAH",
      Some(ByteBuffer.wrap("BEH".getBytes)), 0, 3)()
    map3.foreach { m =>
      assert(m.traceId === span1.traceId)
    }
  }

  test("not index empty service name") {
    cassandraIndex.indexServiceName(spanEmptyServiceName)
    val serviceNames = cassandraIndex.getServiceNames()
    assert(serviceNames.isEmpty)
  }

  test("not index empty span name") {
    cassandraIndex.indexSpanNameByService(spanEmptySpanName)
    val spanNames = cassandraIndex.getSpanNames(spanEmptySpanName.name)
    assert(spanNames().isEmpty)
  }
}
