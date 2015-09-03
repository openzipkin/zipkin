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

import com.twitter.algebird.Moments
import com.twitter.conversions.time._
import com.twitter.util.{Future, Time}
import com.twitter.zipkin.common.{Service, _}
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.storage.{Aggregates, Store}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers, OneInstancePerTest}

class ScribeCollectorServiceSpec extends FunSuite with OneInstancePerTest with Matchers with MockitoSugar {
  val mockAggregates = mock[Aggregates]

  test("store dependencies") {
    val m1 = Moments(2)
    val m2 = Moments(4)
    val dl1 = DependencyLink(Service("tfe"), Service("mobileweb"), m1)
    val dl3 = DependencyLink(Service("Gizmoduck"), Service("tflock"), m2)
    val deps1 = Dependencies(Time.fromSeconds(0), Time.fromSeconds(0)+1.hour, List(dl1, dl3))

    when(mockAggregates.storeDependencies(deps1)) thenReturn Future.Done

    val cs = new ScribeCollectorInterface(Store(null, mockAggregates), null, null);
    cs.storeDependencies(deps1.toThrift)

    verify(mockAggregates).storeDependencies(deps1)
  }
}
