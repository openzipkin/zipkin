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

import com.twitter.conversions.time._
import com.twitter.util.{Future, Time}
import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.storage.{DependencyStore, Store}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers, OneInstancePerTest}

class ScribeCollectorServiceSpec extends FunSuite with OneInstancePerTest with Matchers with MockitoSugar {
  val mockDependencies = mock[DependencyStore]

  test("store dependencies") {
    val callCount1 = 2
    val callCount2 = 4
    val dl1 = DependencyLink("tfe", "mobileweb", callCount1)
    val dl3 = DependencyLink("Gizmoduck", "tflock", callCount2)
    val deps1 = Dependencies(Time.fromSeconds(0), Time.fromSeconds(0)+1.hour, List(dl1, dl3))

    when(mockDependencies.storeDependencies(deps1)) thenReturn Future.Done

    val cs = new ScribeCollectorInterface(Store(null, mockDependencies), null, null)
    cs.storeDependencies(deps1.toThrift)

    verify(mockDependencies).storeDependencies(deps1)
  }
}
