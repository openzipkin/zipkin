/*
 * Copyright 2013 Twitter Inc.
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

package com.twitter.zipkin.storage.anormdb

import com.twitter.algebird.Moments
import com.twitter.conversions.time._
import com.twitter.util.{Await, Time}
import com.twitter.zipkin.common.{Dependencies, DependencyLink, Service}
import org.scalatest.FunSuite

class AnormDependencyStoreTest extends FunSuite {
  test("store and get dependencies") {
    val db = new DB(new DBConfig("sqlite-memory", new DBParams(dbName = "zipkinDependenciesTest1")))
    val con = db.install()
    val dependencies = new AnormDependencyStore(db, Some(con))

    val dl1 = new DependencyLink(new Service("parent1"), new Service("child1"), Moments(18))
    val dl2 = new DependencyLink(new Service("parent2"), new Service("child2"), Moments(42))
    val dep1 = new Dependencies(Time.fromSeconds(1), Time.fromSeconds(2), List(dl1, dl2))

    Await.result(dependencies.storeDependencies(dep1))

    val agg1 = Await.result(dependencies.getDependencies(Some(dep1.startTime), Some(dep1.endTime))) // Inclusive, start to end
    val agg2 = Await.result(dependencies.getDependencies(Some(Time.fromSeconds(0)), Some(Time.now))) // All time
    val agg3 = Await.result(dependencies.getDependencies(Some(Time.fromSeconds(0)), None)) // 0 to +1.day

    val agg4 = Await.result(dependencies.getDependencies(Some(Time.fromSeconds(0)), Some(Time.fromSeconds(1) + 1.millisecond))) // end inside the dependency
    val agg5 = Await.result(dependencies.getDependencies(Some(Time.fromSeconds(1) + 1.millisecond), Some(Time.fromSeconds(2) - 1.millisecond))) // start and end inside the dependency
    val agg6 = Await.result(dependencies.getDependencies(Some(Time.fromSeconds(1) + 1.millisecond), Some(Time.fromSeconds(3)))) // start inside the dependency

    assert(agg1.links === dep1.links)
    assert(agg2.links === dep1.links)
    assert(agg3.links === dep1.links)

    assert(agg4.links.isEmpty)
    assert(agg5.links.isEmpty)
    assert(agg6.links.isEmpty)

    con.close()
  }
}
