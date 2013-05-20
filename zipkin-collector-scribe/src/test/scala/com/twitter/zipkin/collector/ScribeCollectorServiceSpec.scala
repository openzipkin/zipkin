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
package com.twitter.zipkin.collector

import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.zipkin.common.{Span, Annotation}
import com.twitter.zipkin.config.sampler.AdjustableRateConfig
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.gen
import com.twitter.zipkin.storage.{Store, Aggregates}
import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}
import com.twitter.util.Await

class ScribeCollectorServiceSpec extends Specification with JMocker with ClassMocker {
  val serializer = new BinaryThriftStructSerializer[gen.Span] {
    def codec = gen.Span
  }
  val category = "zipkin"

  val validSpan = Span(123, "boo", 456, None, List(new Annotation(1, "bah", None)), Nil)
  val validList = List(gen.LogEntry(category, serializer.toString(validSpan.toThrift)))

  val wrongCatList = List(gen.LogEntry("wrongcat", serializer.toString(validSpan.toThrift)))

  val base64 = "CgABAAAAAAAAAHsLAAMAAAADYm9vCgAEAAAAAAAAAcgPAAYMAAAAAQoAAQAAAAAAAAABCwACAAAAA2JhaAAPAAgMAAAAAAIACQAA"

  val queue = mock[WriteQueue[Seq[String]]]
  val zkSampleRateConfig = mock[AdjustableRateConfig]
  val mockAggregates = mock[Aggregates]

  def scribeCollectorService = new ScribeCollectorService(queue, Seq(Store(null, null, mockAggregates)), Set(category)) {
    running = true
  }

  "ScribeCollectorService" should {
    "add to queue" in {
      val cs = scribeCollectorService

      expect {
        one(queue).add(List(base64)) willReturn(true)
      }

      gen.ResultCode.Ok mustEqual Await.result(cs.log(validList))
    }

    "push back" in {
      val cs = scribeCollectorService

      expect {
        one(queue).add(List(base64)) willReturn(false)
      }

      gen.ResultCode.TryLater mustEqual Await.result(cs.log(validList))
    }

    "ignore wrong category" in {
      val cs = scribeCollectorService

      expect {
        never(queue).add(any)
      }

      gen.ResultCode.Ok mustEqual Await.result(cs.log(wrongCatList))
    }

    "store aggregates" in {
      val serviceName = "mockingbird"
      val annotations = Seq("a" , "b", "c")
      val dependencies = Seq("service1:10", "service2:5")

      "store top annotations" in {
        val cs = scribeCollectorService

        expect {
          one(mockAggregates).storeTopAnnotations(serviceName, annotations)
        }

        cs.storeTopAnnotations(serviceName, annotations)
      }

      "store top key value annotations" in {
        val cs = scribeCollectorService

        expect {
          one(mockAggregates).storeTopKeyValueAnnotations(serviceName, annotations)
        }

        cs.storeTopKeyValueAnnotations(serviceName, annotations)
      }

      "store dependencies" in {
        val cs = scribeCollectorService
        expect {
          one(mockAggregates).storeDependencies(serviceName, dependencies)
        }

        cs.storeDependencies(serviceName, dependencies)
      }
    }
  }
}
