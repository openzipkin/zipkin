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
package com.twitter.zipkin.storage

import com.twitter.zipkin.query.{QueryService, adjusters}
import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}
import com.twitter.util.{Await, Future}

class TraceIdsToDurationSpec extends Specification with JMocker with ClassMocker {

  "TraceIdsToDuration" should {

    "fetch durations in batches" in {
      val index = mock[Index]
      val duration1 = TraceIdDuration(1L, 100L, 500L)
      val duration2 = TraceIdDuration(2L, 200L, 600L)
      val duration3 = TraceIdDuration(3L, 300L, 700L)

      expect {
        1.of(index).getTracesDuration(List(1L, 2L)) willReturn Future(List(duration1, duration2))
        1.of(index).getTracesDuration(List(3L)) willReturn Future(List(duration3))
      }

      val td = new TraceIdsToDuration(index, 2)
      td.append(1)
      td.append(2)
      td.append(3)

      val durations = Await.result(td.getDurations())
      durations(0) mustEqual duration1
      durations(1) mustEqual duration2
      durations(2) mustEqual duration3
    }
  }
}