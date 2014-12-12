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
import com.twitter.zipkin.common._
import com.twitter.zipkin.config.sampler.AdjustableRateConfig
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.thriftscala
import com.twitter.zipkin.storage.{Store, Aggregates}
import org.specs.SpecificationWithJUnit
import org.specs.mock.{ClassMocker, JMocker}
import com.twitter.util.{Time, Await}
import com.twitter.conversions.time._
import org.junit.runner.RunWith
import org.specs.runner.JUnitSuiteRunner
import com.twitter.algebird.Moments
import com.twitter.zipkin.common.Service
import com.twitter.zipkin.common.Annotation

@RunWith(classOf[JUnitSuiteRunner])
class ScribeCollectorServiceSpec extends SpecificationWithJUnit with JMocker with ClassMocker {
  val serializer = new BinaryThriftStructSerializer[thriftscala.Span] {
    def codec = thriftscala.Span
  }
  val category = "zipkin"

  val validSpan = Span(123, "boo", 456, None, List(new Annotation(1, "bah", None)), Nil)
  val validList = List(thriftscala.LogEntry(category, serializer.toString(validSpan.toThrift)))

  val wrongCatList = List(thriftscala.LogEntry("wrongcat", serializer.toString(validSpan.toThrift)))

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

      thriftscala.ResultCode.Ok mustEqual Await.result(cs.log(validList))
    }

    "push back" in {
      val cs = scribeCollectorService

      expect {
        one(queue).add(List(base64)) willReturn(false)
      }

      thriftscala.ResultCode.TryLater mustEqual Await.result(cs.log(validList))
    }

    "ignore wrong category" in {
      val cs = scribeCollectorService

      expect {
        never(queue).add(any)
      }

      thriftscala.ResultCode.Ok mustEqual Await.result(cs.log(wrongCatList))
    }

    "store dependencies" in {
      val cs = scribeCollectorService
      val m1 = Moments(2)
      val m2 = Moments(4)
      val dl1 = DependencyLink(Service("tfe"), Service("mobileweb"), m1)
      val dl3 = DependencyLink(Service("Gizmoduck"), Service("tflock"), m2)
      val deps1 = Dependencies(Time.fromSeconds(0), Time.fromSeconds(0)+1.hour, List(dl1, dl3))

      expect {
        one(mockAggregates).storeDependencies(deps1)
      }

      cs.storeDependencies(deps1.toThrift)
    }

    "store aggregates" in {
      val serviceName = "mockingbird"
      val annotations = Seq("a" , "b", "c")

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
    }
  }
}
