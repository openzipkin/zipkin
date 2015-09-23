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
package com.twitter.zipkin.common

import com.twitter.algebird.{Monoid, Semigroup}
import com.twitter.conversions.time._
import com.twitter.util.Time
import org.scalatest.FunSuite

class DependenciesTest extends FunSuite {
  test("DependencyLinks") {
    val callCount1 = 2
    val callCount2 = 4
    val d1 = DependencyLink("tfe", "mobileweb", callCount1)
    val d2 = DependencyLink("tfe", "mobileweb", callCount2)
    val d3 = DependencyLink("Gizmoduck", "tflock", callCount2)

    // combine
    assert(Semigroup.plus(d1, d2) === d1.copy(callCount = callCount1 + callCount2))

    // assert if incompatible links are combined
    intercept[AssertionError] {
      Semigroup.plus(d1, d3)
    }
  }


  test("Dependencies") {
    val callCount1 = 2
    val callCount2 = 4
    val dl1 = DependencyLink("tfe", "mobileweb", callCount1)
    val dl2 = DependencyLink("tfe", "mobileweb", callCount2)
    val dl3 = DependencyLink("Gizmoduck", "tflock", callCount2)
    val dl4 = DependencyLink("mobileweb", "Gizmoduck", callCount2)
    val dl5 = dl1.copy(callCount = callCount1 + callCount2)

    val deps1 = Dependencies(Time.fromSeconds(0), Time.fromSeconds(0) + 1.hour, List(dl1, dl3))
    val deps2 = Dependencies(Time.fromSeconds(0) + 1.hour, Time.fromSeconds(0) + 2.hours, List(dl2, dl4))

    // express identity when added to zero
    val result = Monoid.plus(deps1, Monoid.zero[Dependencies])
    assert(result === deps1)

    // combine
    val result2 = Monoid.plus(deps1, deps2)

    assert(result2.startTime === Time.fromSeconds(0))
    assert(result2.endTime === Time.fromSeconds(0) + 2.hours)

    assert(result2.links == Seq(dl5, dl3, dl4))
  }
}
