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

import java.util.concurrent.TimeUnit.{HOURS, MICROSECONDS}

import org.scalatest.{FunSuite, Matchers}

class DependenciesTest extends FunSuite with Matchers {

  val dl1 = DependencyLink("Gizmoduck", "tflock", 4)
  val dl2 = DependencyLink("mobileweb", "Gizmoduck", 4)
  val dl3 = DependencyLink("tfe", "mobileweb", 2)
  val dl4 = DependencyLink("tfe", "mobileweb", 4)

  val deps1 = Dependencies(0L, MICROSECONDS.convert(1, HOURS), List(dl1, dl3))
  val deps2 = Dependencies(MICROSECONDS.convert(1, HOURS), MICROSECONDS.convert(2, HOURS), List(dl2, dl4))

  test("identity on Dependencies.zero") {
    deps1 + Dependencies.zero should be(deps1)
    Dependencies.zero + deps1 should be(deps1)
  }

  test("sums where parent/child match") {
    val result = deps1 + deps2
    result.startTs should be(deps1.startTs)
    result.endTs should be(deps2.endTs)
    result.links.sortBy(_.parent) should be(Seq(
      dl1,
      dl2,
      dl3.copy(callCount = dl3.callCount + dl4.callCount)
    ))
  }
}
