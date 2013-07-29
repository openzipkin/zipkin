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
import com.twitter.cassie._
import com.twitter.util.{Await, Future, Time}
import com.twitter.conversions.time._
import com.twitter.zipkin.cassandra.{AggregatesBuilder, Keyspace}
import org.specs.mock.{ClassMocker, JMocker}
import org.specs.SpecificationWithJUnit
import scala.collection.JavaConverters._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.gen
import com.twitter.algebird.Moments
import com.twitter.zipkin.common.{Dependencies, Service, DependencyLink}
import org.junit.runner.RunWith
import org.specs.runner.JUnitSuiteRunner

@RunWith(classOf[JUnitSuiteRunner])
class CassandraAggregatesSpec extends SpecificationWithJUnit with JMocker with ClassMocker {

  val mockKeyspace = mock[Keyspace]
  val mockAnnotationsCf = mock[ColumnFamily[String, Long, String]]
  val mockDependenciesCf = mock[ColumnFamily[Long, Long, gen.Dependencies]]

  def cassandraAggregates = CassandraAggregates(mockKeyspace, mockAnnotationsCf, mockDependenciesCf)

  def column(name: Long, value: String) = new Column[Long, String](name, value)

  "CassandraAggregates" should {
    val topAnnsSeq = Seq("finagle.retry", "finagle.timeout", "annotation1", "annotation2")
    val topAnns = topAnnsSeq.zipWithIndex.map { case (ann, index) =>
      index.toLong -> column(index, ann)
    }.toMap.asJava

    val serviceCallsSeq = Seq("parent1:10, parent2:20")
    val serviceCalls = serviceCallsSeq.zipWithIndex.map {case (ann, index) =>
      index.toLong -> column(index, ann)
    }.toMap.asJava

    "retrieve" in {
      "Top Annotations" in {
        val agg = cassandraAggregates
        val serviceName = "mockingbird"
        val rowKey = agg.topAnnotationRowKey(serviceName)

        expect {
          one(mockAnnotationsCf).getRow(rowKey) willReturn Future.value(topAnns)
        }

        agg.getTopAnnotations(serviceName)() mustEqual topAnnsSeq
      }

      "Top KeyValue Annotations" in {
        val agg = cassandraAggregates
        val serviceName = "mockingbird"
        val rowKey = agg.topKeyValueRowKey(serviceName)

        expect {
          one(mockAnnotationsCf).getRow(rowKey) willReturn Future.value(topAnns)
        }

        agg.getTopKeyValueAnnotations(serviceName)() mustEqual topAnnsSeq
      }

      "Dependencies" in {
        val agg = cassandraAggregates
        val m1 = Moments(2)
        val m2 = Moments(4)
        val dl1 = DependencyLink(Service("tfe"), Service("mobileweb"), m1)
        val dl3 = DependencyLink(Service("Gizmoduck"), Service("tflock"), m2)
        val deps1 = Dependencies(Time.fromSeconds(0), Time.fromSeconds(0)+1.hour, List(dl1, dl3))
        val col = new Column[Long, gen.Dependencies](0L, deps1.toThrift)

        expect {
          one(mockDependenciesCf).multigetRows(Set(0L, 1.day.inMicroseconds).asJava, None, None, Order.Normal, Int.MaxValue) willReturn Future.value(Map(0L -> Map(0L -> col).asJava).asJava)
        }

        val result = Await.result(agg.getDependencies(Time.fromSeconds(0)))
        result mustEqual deps1
      }
    }

    "storage" in {
      object FakeServer extends FakeCassandra
      var agg: CassandraAggregates = null
      val serviceName = "mockingbird"

      doBefore {
        FakeServer.start()
        val keyspaceBuilder = Keyspace.static(port = FakeServer.port.get)
        val builder = AggregatesBuilder(keyspaceBuilder)
        agg = builder.apply()
      }

      doAfter {
        agg.close()
        FakeServer.stop()
      }

      "storeTopAnnotations" in {
        agg.storeTopAnnotations(serviceName, topAnnsSeq).apply()
        agg.getTopAnnotations(serviceName).apply() mustEqual topAnnsSeq
      }

      "storeTopKeyValueAnnotations" in {
        agg.storeTopKeyValueAnnotations(serviceName, topAnnsSeq).apply()
        agg.getTopKeyValueAnnotations(serviceName).apply() mustEqual topAnnsSeq
      }

      "storeDependencies" in {
        val m1 = Moments(2)
        val m2 = Moments(4)
        val dl1 = DependencyLink(Service("tfe"), Service("mobileweb"), m1)
        val dl3 = DependencyLink(Service("Gizmoduck"), Service("tflock"), m2)
        val deps1 = Dependencies(Time.fromSeconds(0), Time.fromSeconds(0)+1.hour, List(dl1, dl3))

        Await.result(agg.storeDependencies(deps1))
        Await.result(agg.getDependencies(Time.fromSeconds(0))) mustEqual deps1
      }

      "clobber old entries" in {
        val anns1 = Seq("a1", "a2", "a3", "a4")
        val anns2 = Seq("a5", "a6")

        agg.storeTopAnnotations(serviceName, anns1).apply()
        agg.getTopAnnotations(serviceName).apply() mustEqual anns1

        agg.storeTopAnnotations(serviceName, anns2).apply()
        agg.getTopAnnotations(serviceName).apply() mustEqual anns2
      }
    }
  }
}
