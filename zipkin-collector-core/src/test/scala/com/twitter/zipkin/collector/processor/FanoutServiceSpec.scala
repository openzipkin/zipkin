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

import com.twitter.finagle.Service
import com.twitter.util.Future
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class FanoutServiceSpec extends FunSuite with Matchers with MockitoSugar {
  test("fanout") {
    val serv1 = mock[Service[Int, Unit]]
    val serv2 = mock[Service[Int, Unit]]

    val fanout = new FanoutService[Int](Seq(serv1, serv2))
    val item = 1

    when(serv1.apply(item)) thenReturn Future.Done
    when(serv2.apply(item)) thenReturn Future.Done

    fanout.apply(item)

    verify(serv1).apply(item)
    verify(serv2).apply(item)
  }
}
