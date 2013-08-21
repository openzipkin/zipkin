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

import org.specs.SpecificationWithJUnit
import org.specs.runner.JUnitSuiteRunner
import org.junit.runner.RunWith

import com.twitter.algebird.{Semigroup, Moments, Monoid}
import com.twitter.util.Time
import com.twitter.conversions.time._

@RunWith(classOf[JUnitSuiteRunner])
class DependenciesSpec extends SpecificationWithJUnit
{
  "Services" should {
    "compare correctly" in {
      val s1 = Service("foo")
      val s2 = Service("bar")
      val s3 = Service("foo")
      val s4 = Service("Foo")
      val s5 = Service("FOO")

      s1 mustEqual s1
      s1 mustEqual s3
      s1 mustNotEq s2
      s1 mustNotEq s4 // not sure if case sensitivity is required, but we should be aware if it changes
      s1 mustNotEq s5
    }
  }

  "DependencyLinks" should {
    val m1 = Moments(2)
    val m2 = Moments(4)
    val d1 = DependencyLink(Service("tfe"), Service("mobileweb"), m1)
    val d2 = DependencyLink(Service("tfe"), Service("mobileweb"), m2)
    val d3 = DependencyLink(Service("Gizmoduck"), Service("tflock"), m2)

    "combine" in {
      Semigroup.plus(d1, d2) mustEqual d1.copy(durationMoments = Monoid.plus(m1, m2))
    }

    "assert if incompatible links are combined" in {
      Semigroup.plus(d1, d3) must throwA[AssertionError]
    }
  }


  "Dependencies" should {
    val m1 = Moments(2)
    val m2 = Moments(4)
    val dl1 = DependencyLink(Service("tfe"), Service("mobileweb"), m1)
    val dl2 = DependencyLink(Service("tfe"), Service("mobileweb"), m2)
    val dl3 = DependencyLink(Service("Gizmoduck"), Service("tflock"), m2)
    val dl4 = DependencyLink(Service("mobileweb"), Service("Gizmoduck"), m2)
    val dl5 = dl1.copy(durationMoments = Monoid.plus(m1,m2))

    val deps1 = Dependencies(0, 1.hour.inMicroseconds, List(dl1, dl3))
    val deps2 = Dependencies(1.hour.inMicroseconds, 2.hours.inMicroseconds, List(dl2, dl4))

    "express identity when added to zero" in {
      val result = Monoid.plus(deps1, Monoid.zero[Dependencies])
      result mustEqual deps1
    }

    "combine" in {
      val result = Monoid.plus(deps1, deps2)

      result.startTime mustEqual Time.fromSeconds(0)
      result.endTime mustEqual Time.fromSeconds(0)+2.hours
      result.links must haveTheSameElementsAs(Seq(dl4, dl5, dl3))
    }
  }
}
