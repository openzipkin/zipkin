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
package com.twitter.zipkin.collector.processor

import org.specs.mock.{JMocker, ClassMocker}
import org.specs.Specification

class ProcessorFilterSpec extends Specification with JMocker with ClassMocker {
  "ProcessorFilter" should {
    "compose" in {
      val filter1 = new ProcessorFilter[Int, Double] {
        def apply(item: Int) = (item + 1).toDouble
      }
      val filter2 = new ProcessorFilter[Double, Long] {
        def apply(item: Double) = (item + 1).toLong
      }

      "with other filter" in {
        val composed = filter1 andThen filter2
        val item = 1
        val expected = 3L

        val actual = composed.apply(item)
        actual mustEqual expected
      }

      "with Processor" in {
        val proc = mock[Processor[Long]]
        val composed = filter1 andThen filter2 andThen proc

        val item = 1
        val procExpected = 3L

        expect {
          one(proc).process(procExpected)
        }

        composed.process(item)
      }
    }
  }
}
