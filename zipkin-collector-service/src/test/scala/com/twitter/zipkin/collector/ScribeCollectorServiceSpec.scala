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

import com.twitter.util.Future
import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.storage.{DependencyStore, Store}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers, OneInstancePerTest}
import java.util.concurrent.TimeUnit.{HOURS, MICROSECONDS}

class ScribeCollectorServiceSpec extends FunSuite with OneInstancePerTest with Matchers with MockitoSugar {
  val mockDependencies = mock[DependencyStore]

  test("store dependencies") {
    val dep = new Dependencies(0L, 0 + MICROSECONDS.convert(1, HOURS), List(
      new DependencyLink("zipkin-web", "zipkin-query", 18),
      new DependencyLink("zipkin-query", "cassandra", 42)
    ))

    when(mockDependencies.storeDependencies(dep)) thenReturn Future.Done

    val cs = new ScribeCollectorInterface(Store(null, mockDependencies), null, null)
    cs.storeDependencies(dep.toThrift)

    verify(mockDependencies).storeDependencies(dep)
  }
}
