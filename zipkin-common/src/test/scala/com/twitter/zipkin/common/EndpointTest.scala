/*
 * Copyright 2014 Twitter Inc.
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

import java.net.InetSocketAddress

import org.scalatest.FunSuite

class EndpointTest extends FunSuite {
  val google  = Endpoint(  134744072, -80, "google")
  val example = Endpoint(-1073730806,  21, "example")
  val twitter = Endpoint( -952396249, 443, "twitter")

  /** Representations should lowercase on the way in */
  test("serviceName cannot be lowercase") {
    intercept[IllegalArgumentException] {
      Endpoint( -952396249, 443, "Twitter")
    }
  }

  test("compare correctly") {
    val e1 = Endpoint(123, 456, "a")
    val e2 = Endpoint(123, 457, "a")
    val e3 = Endpoint(124, 456, "a")
    val e4 = Endpoint(123, 456, "b")

    assert(e1.compare(e1) === 0)
    assert(e1.compare(e2) < 0)
    assert(e1.compare(e3) < 0)
    assert(e1.compare(e4) < 0)
  }

  test("convert to dotted-quad strings") {
    assert(google.getHostAddress === "8.8.8.8")
    assert(example.getHostAddress === "192.0.43.10")
    assert(twitter.getHostAddress === "199.59.150.39")
  }

  test("provide unsigned port") {
    assert(google.getUnsignedPort === 65456)
    assert(example.getUnsignedPort === 21)
    assert(twitter.getUnsignedPort === 443)
  }

  test("convert to java.net.InetSocketAddress") {
    val gAddr = new InetSocketAddress("8.8.8.8", 65456)
    val eAddr = new InetSocketAddress("192.0.43.10", 21)
    val tAddr = new InetSocketAddress("199.59.150.39", 443)

    assert(google.getInetSocketAddress === gAddr)
    assert(example.getInetSocketAddress === eAddr)
    assert(twitter.getInetSocketAddress === tAddr)
  }
}
