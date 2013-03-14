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

import org.specs.Specification
import java.net.InetSocketAddress

class EndpointSpec extends Specification {
  "Endpoint" should {

    val google  = Endpoint(  134744072, -80, "google")
    val example = Endpoint(-1073730806,  21, "example")
    val twitter = Endpoint( -952396249, 443, "twitter")

    "compare correctly" in {
      val e1 = Endpoint(123, 456, "a")
      val e2 = Endpoint(123, 457, "a")
      val e3 = Endpoint(124, 456, "a")
      val e4 = Endpoint(123, 456, "b")

      e1.compare(e1) mustEqual 0
      e1.compare(e2) must beLessThan(0)
      e1.compare(e3) must beLessThan(0)
      e1.compare(e4) must beLessThan(0)
    }

    "convert to dotted-quad strings" in {
      google.getHostAddress  mustEqual       "8.8.8.8"
      example.getHostAddress mustEqual   "192.0.43.10"
      twitter.getHostAddress mustEqual "199.59.150.39"
    }

    "provide unsigned port" in {
      google.getUnsignedPort  mustEqual 65456
      example.getUnsignedPort mustEqual    21
      twitter.getUnsignedPort mustEqual   443
    }

    "convert to java.net.InetSocketAddress" in {
      val gAddr = new InetSocketAddress(      "8.8.8.8", 65456)
      val eAddr = new InetSocketAddress(  "192.0.43.10",    21)
      val tAddr = new InetSocketAddress("199.59.150.39",   443)

      google.getInetSocketAddress  mustEqual gAddr
      example.getInetSocketAddress mustEqual eAddr
      twitter.getInetSocketAddress mustEqual tAddr
    }
  }
}
