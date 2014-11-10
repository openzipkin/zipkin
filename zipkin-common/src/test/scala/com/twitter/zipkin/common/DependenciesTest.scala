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

import com.twitter.algebird.{Semigroup, Moments, Monoid}
import com.twitter.util.Time
import com.twitter.conversions.time._
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class DependenciesTest extends FunSuite {
  test("services compare correctly") {
    val s1 = Service("foo")
    val s2 = Service("bar")
    val s3 = Service("foo")
    val s4 = Service("Foo")
    val s5 = Service("FOO")

    assert(s1 === s1)
    assert(s1 === s3)
    assert(s1 !=  s2)
    assert(s1 !=  s4) // not sure if case sensitivity is required, but we should be aware if it changes
    assert(s1 !=  s5)
  }

  test("DependencyLinks") {
    val m1 = Moments(2)
    val m2 = Moments(4)
    val d1 = DependencyLink(Service("tfe"), Service("mobileweb"), m1)
    val d2 = DependencyLink(Service("tfe"), Service("mobileweb"), m2)
    val d3 = DependencyLink(Service("Gizmoduck"), Service("tflock"), m2)

    // combine
    assert(Semigroup.plus(d1, d2) === d1.copy(durationMoments = Monoid.plus(m1, m2)))

    // assert if incompatible links are combined
    intercept[AssertionError] { Semigroup.plus(d1, d3) }
  }


  test("Dependencies") {
    val m1 = Moments(2)
    val m2 = Moments(4)
    val dl1 = DependencyLink(Service("tfe"), Service("mobileweb"), m1)
    val dl2 = DependencyLink(Service("tfe"), Service("mobileweb"), m2)
    val dl3 = DependencyLink(Service("Gizmoduck"), Service("tflock"), m2)
    val dl4 = DependencyLink(Service("mobileweb"), Service("Gizmoduck"), m2)
    val dl5 = dl1.copy(durationMoments = Monoid.plus(m1,m2))

    val deps1 = Dependencies(Time.fromSeconds(0), Time.fromSeconds(0)+1.hour, List(dl1, dl3))
    val deps2 = Dependencies(Time.fromSeconds(0)+1.hour, Time.fromSeconds(0)+2.hours, List(dl2, dl4))

    // express identity when added to zero
    val result = Monoid.plus(deps1, Monoid.zero[Dependencies])
    assert(result === deps1)

    // combine
    val result2 = Monoid.plus(deps1, deps2)

    assert(result2.startTime === Time.fromSeconds(0))
    assert(result2.endTime === Time.fromSeconds(0)+2.hours)

    def counts(e: Traversable[_]) = e groupBy identity mapValues (_.size)
    assert(counts(result2.links) == counts(Seq(dl4, dl5, dl3)))
  }
}
