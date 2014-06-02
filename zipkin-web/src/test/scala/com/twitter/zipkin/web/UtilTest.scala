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
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
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
}
