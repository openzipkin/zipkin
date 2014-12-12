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

import com.twitter.algebird.Moments
import com.twitter.cassie._
import com.twitter.cassie.tests.util.FakeCassandra
import com.twitter.conversions.time._
import com.twitter.util.{Await, Future, Time}
import com.twitter.zipkin.cassandra.{AggregatesBuilder, Keyspace}
import com.twitter.zipkin.common.{Dependencies, Service, DependencyLink}
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.thriftscala
import java.nio.ByteBuffer
import org.junit.runner.RunWith
import org.mockito.Matchers._
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class CassandraAggregatesTest extends FunSuite with MockitoSugar with BeforeAndAfter {

  val mockKeyspace = mock[Keyspace]
  val mockAnnotationsCf = mock[ColumnFamily[String, Long, String]]
  val mockDependenciesCf = mock[ColumnFamily[ByteBuffer, Long, thriftscala.Dependencies]]

  def cassandraAggregates = CassandraAggregates(mockKeyspace, mockAnnotationsCf, mockDependenciesCf)

  def column(name: Long, value: String) = new Column[Long, String](name, value)

  val topAnnsSeq = Seq("finagle.retry", "finagle.timeout", "annotation1", "annotation2")
  val topAnns = topAnnsSeq.zipWithIndex.map { case (ann, index) =>
    index.toLong -> column(index, ann)
  }.toMap.asJava

  val serviceCallsSeq = Seq("parent1:10, parent2:20")
  val serviceCalls = serviceCallsSeq.zipWithIndex.map {case (ann, index) =>
    index.toLong -> column(index, ann)
  }.toMap.asJava

  test("Top Annotations") {
    val agg = cassandraAggregates
    val serviceName = "mockingbird"
    val rowKey = agg.topAnnotationRowKey(serviceName)

    when(mockAnnotationsCf.getRow(rowKey)).thenReturn(Future.value(topAnns))
    assert(agg.getTopAnnotations(serviceName)() === topAnnsSeq)
  }

  test("Top KeyValue Annotations") {
    val agg = cassandraAggregates
    val serviceName = "mockingbird"
    val rowKey = agg.topKeyValueRowKey(serviceName)

    when(mockAnnotationsCf.getRow(rowKey)).thenReturn(Future.value(topAnns))
    assert(agg.getTopKeyValueAnnotations(serviceName)() === topAnnsSeq)
  }

  object FakeServer extends FakeCassandra
  var agg: CassandraAggregates = null
  val serviceName = "mockingbird"

  before {
    FakeServer.start()
    val keyspaceBuilder = Keyspace.static(port = FakeServer.port.get)
    val builder = AggregatesBuilder(keyspaceBuilder)
    agg = builder.apply()
  }

  after {
    agg.close()
    FakeServer.stop()
  }

  test("storeTopAnnotations") {
    agg.storeTopAnnotations(serviceName, topAnnsSeq).apply()
    assert(agg.getTopAnnotations(serviceName).apply() === topAnnsSeq)
  }

  test("storeTopKeyValueAnnotations") {
    agg.storeTopKeyValueAnnotations(serviceName, topAnnsSeq).apply()
    assert(agg.getTopKeyValueAnnotations(serviceName).apply() === topAnnsSeq)
  }

  test("storeDependencies") {
    val m1 = Moments(2)
    val m2 = Moments(4)
    val dl1 = DependencyLink(Service("tfe"), Service("mobileweb"), m1)
    val dl3 = DependencyLink(Service("Gizmoduck"), Service("tflock"), m2)
    val deps1 = Dependencies(Time.fromSeconds(0), Time.fromSeconds(0)+1.hour, List(dl1, dl3))

    // ideally we'd like to retrieve the stored deps but FakeCassandra does not support
    // the retrieval mechanism we use to get out dependencies.
    // check this doesn't throw an exception
    Await.result(agg.storeDependencies(deps1))
  }

  test("clobber old entries") {
    val anns1 = Seq("a1", "a2", "a3", "a4")
    val anns2 = Seq("a5", "a6")

    agg.storeTopAnnotations(serviceName, anns1).apply()
    assert(agg.getTopAnnotations(serviceName).apply() === anns1)

    agg.storeTopAnnotations(serviceName, anns2).apply()
    assert(agg.getTopAnnotations(serviceName).apply() === anns2)
  }
}
