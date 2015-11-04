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
package com.twitter.zipkin.web

import com.twitter.conversions.time._
import com.twitter.zipkin.Constants
import com.twitter.zipkin.common.{Annotation, Endpoint, Span}
import org.scalatest.FunSuite

class UtilTest extends FunSuite {
  import Util._

  test("durationStr") {
    Map(
      // round numbers (singular and plural)
      9.microseconds -> "9μ",
      9.milliseconds -> "9.000ms",
      9.seconds -> "9.000s",
      9.minutes -> "9min",
      1.hour -> "1hr",
      9.hours -> "9hrs",
      1.day -> "1day",
      9.days -> "9days",

      // millis with micros should be a decimal
      9.milliseconds + 500.microseconds -> "9.500ms",

      // seconds with millis should be a decimal and ignore micros
      1.second + 500.milliseconds -> "1.500s",
      1.second + 500.milliseconds + 600.microseconds -> "1.500s",

      // complex combinations (micros are ignored at this level)
      1.day + 3.hours + 5.minutes + 7.seconds + 900.milliseconds -> "1day 3hrs 5min 7.900s",
      1.day + 3.hours + 5.minutes + 7.seconds + 900.milliseconds + 100.microseconds -> "1day 3hrs 5min 7.900s"
    ) foreach { case (t, v) =>
      // test as Duration
      assert(durationStr(t) === v)

      // test as Long
      assert(durationStr(t.inNanoseconds) === v)
    }
  }

  test("get duration of trace") {
    val annotations = List(Annotation(100, Constants.ClientSend, Some(Endpoint(123, 123, "service1"))),
      Annotation(200, Constants.ClientRecv, Some(Endpoint(123, 123, "service1"))))
    val span = Span(12345, "methodcall", 666, None, annotations)
    assert(duration(List(span)) === 100)
  }

  test("get duration of trace without root span") {
    val annotations = List(Annotation(100, Constants.ClientSend, Some(Endpoint(123, 123, "service1"))),
      Annotation(200, Constants.ClientRecv, Some(Endpoint(123, 123, "service1"))))
    val span = Span(12345, "methodcall", 666, Some(123), annotations)
    val annotations2 = List(Annotation(150, Constants.ClientSend, Some(Endpoint(123, 123, "service1"))),
      Annotation(160, Constants.ClientRecv, Some(Endpoint(123, 123, "service1"))))
    val span2 = Span(12345, "methodcall", 666, Some(123), annotations2)
    assert(duration(List(span, span2)) === 100)
  }

  test("get correct duration for imbalanced spans") {
    val ann1 = List(
      Annotation(0, "Client send", None)
    )
    val ann2 = List(
      Annotation(1, "Server receive", None),
      Annotation(12, "Server send", None)
    )

    val span1 = Span(123, "method_1", 100, None, ann1)
    val span2 = Span(123, "method_2", 200, Some(100), ann2)

    assert(duration(List(span1, span2)) === 12)
  }
}
