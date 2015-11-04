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

import java.util.Locale

import com.twitter.util.Duration
import com.twitter.zipkin.Constants.CoreAnnotationNames
import java.util.concurrent.TimeUnit
import com.twitter.zipkin.common.Span

object Util {
  private[this] val TimeUnits = Seq(
    TimeUnit.DAYS,
    TimeUnit.HOURS,
    TimeUnit.MINUTES,
    TimeUnit.SECONDS,
    TimeUnit.MILLISECONDS,
    TimeUnit.MICROSECONDS)

  private[this] val TimeUnitNames = Map(
    TimeUnit.DAYS -> ("days", "day"),
    TimeUnit.HOURS -> ("hrs", "hr"),
    TimeUnit.MINUTES -> ("min", "min"))

  def durationStr(durationNs: Long): String =
    durationStr(Duration.fromNanoseconds(durationNs))

  def durationStr(d: Duration): String = {
    var ns = d.inNanoseconds
    TimeUnits.dropWhile(_.convert(ns, TimeUnit.NANOSECONDS) == 0) match {
      case s if s.size == 0 => ""

      // seconds
      case Seq(_, _, _) => "%.3fs".formatLocal(Locale.ENGLISH, d.inMilliseconds / 1000.0)

      // milliseconds
      case Seq(_, _) => "%.3fms".formatLocal(Locale.ENGLISH, d.inMicroseconds / 1000.0)

      // microseconds
      case Seq(_) => "%dμ".formatLocal(Locale.ENGLISH, d.inMicroseconds)

      // seconds or more
      case _ =>
        val s = new StringBuilder
        for ((u, (pName, sName)) <- TimeUnitNames) {
          val v = u.convert(ns, TimeUnit.NANOSECONDS)
          if (v != 0) {
            ns -= TimeUnit.NANOSECONDS.convert(v, u)
            if (v > 0 && !s.isEmpty)
              s.append(" ")
            s.append(v.toString)
            s.append(if (v == 0 || v > 1) pName else sName)
          }
        }

        if (ns > 0) {
          if (!s.isEmpty)
            s.append(" ")
          s.append(durationStr(ns))
        }

        s.toString()
    }
  }

  def annoToString(value: String): String =
    CoreAnnotationNames.get(value).getOrElse(value)

  def getRootSpans(spans: List[Span]): List[Span] = {
    val idSpan = getIdToSpanMap(spans)
    spans filter { !_.parentId.flatMap(idSpan.get).isDefined }
  }

  /**
   * How long did this span take to run?
   * Returns microseconds between start annotation and end annotation
   */
  def duration(spans: List[Span]): Long = {
    val endTs = spans.flatMap(_.annotations).map(_.timestamp).reduceOption(_ max _)
    (endTs.getOrElse(0L) - spans.headOption.flatMap(_.timestamp).getOrElse(0L))
  }

  /*
   * Turn the Trace into a map of Span Id -> Span
   */
  def getIdToSpanMap(spans: List[Span]): Map[Long, Span] = spans.map(s => (s.id, s)).toMap
}
