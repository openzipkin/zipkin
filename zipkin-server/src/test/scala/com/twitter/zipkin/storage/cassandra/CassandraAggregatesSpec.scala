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

import com.twitter.cassie.{Column, ColumnFamily}
import com.twitter.cassie.tests.util.FakeCassandra
import com.twitter.io.TempFile
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.util.{Eval, Future}
import com.twitter.zipkin.config.CassandraAggregatesConfig
import org.specs.mock.{ClassMocker, JMocker}
import org.specs.Specification
import scala.collection.JavaConverters._

class CassandraAggregatesSpec extends Specification with JMocker with ClassMocker {

  val mockCf = mock[ColumnFamily[String, Long, String]]

  def cassandraAggregates = new CassandraAggregates {
    val topAnnotations = mockCf
  }

  def column(name: Long, value: String) = new Column[Long, String](name, value)

  "CassandraAggregates" should {
    val topAnnsSeq = Seq("finagle.retry", "finagle.timeout", "annotation1", "annotation2")
    val topAnns = topAnnsSeq.zipWithIndex.map { case (ann, index) =>
      index.toLong -> column(index, ann)
    }.toMap.asJava

    "retrieval" in {
      "getTopAnnotations" in {
        val agg = cassandraAggregates
        val serviceName = "mockingbird"
        val rowKey = agg.topAnnotationRowKey(serviceName)

        expect {
          one(mockCf).getRow(rowKey) willReturn Future.value(topAnns)
        }

        agg.getTopAnnotations(serviceName)() mustEqual topAnnsSeq
      }

      "getTopKeyValueAnnotations" in {
        val agg = cassandraAggregates
        val serviceName = "mockingbird"
        val rowKey = agg.topKeyValueRowKey(serviceName)

        expect {
          one(mockCf).getRow(rowKey) willReturn Future.value(topAnns)
        }

        agg.getTopKeyValueAnnotations(serviceName)() mustEqual topAnnsSeq
      }
    }

    "storage" in {
      object FakeServer extends FakeCassandra
      var agg: CassandraAggregates = null
      val serviceName = "mockingbird"

      doBefore {
        FakeServer.start()
        val test = TempFile.fromResourcePath("/CassandraAggregatesConfig.scala")
        val env = RuntimeEnvironment(this, Array("-f", test.toString))
        val config = new Eval().apply[CassandraAggregatesConfig](env.configFile)
        config.cassandraConfig.port = FakeServer.port.get
        agg = config.apply()
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
