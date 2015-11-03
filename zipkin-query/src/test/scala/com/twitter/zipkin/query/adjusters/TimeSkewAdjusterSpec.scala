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
package com.twitter.zipkin.query.adjusters

import com.twitter.zipkin.Constants
import com.twitter.zipkin.common.{Annotation, Endpoint, Span, Trace}
import org.scalatest.FunSuite

import scala.collection._

class TimeSkewAdjusterTest extends FunSuite {
  val endpoint1 = Some(Endpoint(123, 123, "service"))
  val endpoint2 = Some(Endpoint(321, 321, "service"))
  val endpoint3 = Some(Endpoint(456, 456, "service"))

  /*
   * The trace looks as follows
   * endpoint1 calls method1 on endpoint2
   * endpoint2 calls method2 on endpoint3
   *
   * endpoint2 has a clock that is 10 ms before the other endpoints
   *
   * Timings from a constant perspective (with skew in parenthesis)
   * e1 send e2: 100
   * e2 rcvd   : 105 (-10ms e2 skew = 95)
   * e2 send e3: 110 (-10ms e2 skew = 100)
   * e3 rcvd   : 115
   * e3 repl e2: 120
   * e2 rcvd   : 125 (-10ms e2 skew = 115)
   * e2 repl e1: 130 (-10ms e2 skew = 120)
   * e1 rcvd   : 135
   */
  val skewAnn1 = Annotation(100, Constants.ClientSend, endpoint1)
  val skewAnn2 = Annotation(95, Constants.ServerRecv, endpoint2) // skewed
  val skewAnn3 = Annotation(120, Constants.ServerSend, endpoint2) // skewed
  val skewAnn4 = Annotation(135, Constants.ClientRecv, endpoint1)
  val skewSpan1 = Span(1, "method1", 666, None, List(skewAnn1, skewAnn2, skewAnn3, skewAnn4))

  val skewAnn5 = Annotation(100, Constants.ClientSend, endpoint2) // skewed
  val skewAnn6 = Annotation(115, Constants.ServerRecv, endpoint3)
  val skewAnn7 = Annotation(120, Constants.ServerSend, endpoint3)
  val skewAnn8 = Annotation(115, Constants.ClientRecv, endpoint2) // skewed
  val skewSpan2 = Span(1, "method2", 777, Some(666), List(skewAnn5, skewAnn6, skewAnn7, skewAnn8))

  val inputTrace = new Trace(List[Span](skewSpan1, skewSpan2))

  /*
   * Adjusted timings from a constant perspective
   *
   * Timings from a constant perspective (with skew in parenthesis)
   * e1 send e2: 100
   * e2 rcvd   : 105 (-10ms e2 skew = 95)
   * e2 send e3: 110 (-10ms e2 skew = 100)
   * e3 rcvd   : 115
   * e3 repl e2: 120
   * e2 rcvd   : 125 (-10ms e2 skew = 115)
   * e2 repl e1: 130 (-10ms e2 skew = 120)
   * e1 rcvd   : 135
   */
  val expectedAnn1 = Annotation(100, Constants.ClientSend, endpoint1)
  val expectedAnn2 = Annotation(105, Constants.ServerRecv, endpoint2)
  val expectedAnn3 = Annotation(130, Constants.ServerSend, endpoint2)
  val expectedAnn4 = Annotation(135, Constants.ClientRecv, endpoint1)
  val expectedSpan1 = Span(1, "method1", 666, None,
    List(expectedAnn1, expectedAnn2, expectedAnn3, expectedAnn4))

  val expectedAnn5 = Annotation(110, Constants.ClientSend, endpoint2)
  val expectedAnn6 = Annotation(115, Constants.ServerRecv, endpoint3)
  val expectedAnn7 = Annotation(120, Constants.ServerSend, endpoint3)
  val expectedAnn8 = Annotation(125, Constants.ClientRecv, endpoint2)
  val expectedSpan2 = Span(1, "method2", 777, Some(666),
    List(expectedAnn5, expectedAnn6, expectedAnn7, expectedAnn8))

  val expectedTrace = new Trace(List[Span](expectedSpan1, expectedSpan2))


  /*
   * This represents an RPC call where e2 and e3 was not trace enabled.
   *
   * Timings from a constant perspective (with skew in parenthesis)
   * e1 send e2: 100
   * e2 rcvd   : 105 (missing)
   * e2 send e3: 110 (missing)
   * e3 rcvd   : 115 (missing)
   * e3 repl e2: 120 (missing)
   * e2 rcvd   : 125 (missing)
   * e2 repl e1: 130 (missing)
   * e1 rcvd   : 135
   */
  val incompleteAnn1 = Annotation(100, Constants.ClientSend, endpoint1)
  val incompleteAnn4 = Annotation(135, Constants.ClientRecv, endpoint1)
  val incompleteSpan1 = Span(1, "method1", 666, None,
    List(incompleteAnn1, incompleteAnn4))

  val incompleteTrace = new Trace(List[Span](expectedSpan1))

  val epKoalabird = Some(Endpoint(123, 123, "koalabird-cuckoo"))
  val epCuckoo = Some(Endpoint(321, 321, "cuckoo.thrift"))
  val epCassie = Some(Endpoint(456, 456, "cassie"))

  // This is real trace data that currently is not handled well by the adjuster
  val ann1 = Annotation(0, Constants.ServerRecv, epCuckoo) // the server recv is reported as before client send
  val ann2 = Annotation(1, Constants.ClientSend, epKoalabird)
  val ann3 = Annotation(1, Constants.ClientSend, epCassie)
  val ann3F = Annotation(0, Constants.ClientSend, epCassie)
  val ann4 = Annotation(85, Constants.ServerSend, epCuckoo) // reported at the same time, ok
  val ann5 = Annotation(85, Constants.ClientRecv, epKoalabird)
  val ann6 = Annotation(87, Constants.ClientRecv, epCassie)
  val ann6F = Annotation(86, Constants.ClientRecv, epCassie)

  val span1a = Span(1, "values-from-source", 2209720933601260005L, None, List(ann3, ann6))
  val span1aFixed = Span(1, "values-from-source", 2209720933601260005L, None, List(ann3F, ann6F))
  val span1b = Span(1, "values-from-source", 2209720933601260005L, None, List(ann1, ann4))
  // the above two spans are part of the same actual span

  val span2 = Span(1, "multiget_slice", -855543208864892776L, Some(2209720933601260005L),
    List(ann2, ann5))

  val realTrace = new Trace(List(span1a, span1b, span2))
  val expectedRealTrace = new Trace(List(span1aFixed, span1b, span2))

  val adjuster = new TimeSkewAdjuster

  test("adjust span time from machine with incorrect clock") {
    assert(adjuster.adjust(inputTrace) === expectedTrace)
  }

  test("not adjust when there is no clock skew") {
    assert(adjuster.adjust(expectedTrace) === expectedTrace)
  }

  // this happens if the server in an rpc is not trace enabled
  test("not adjust when there are no server spans") {
    assert(adjuster.adjust(incompleteTrace) === incompleteTrace)
  }

  test("not adjust when core annotations are fine") {
    val epTfe = Some(Endpoint(123, 123, "tfe"))
    val epMonorail = Some(Endpoint(456, 456, "monorail"))

    val unicornCs  = Annotation(1L, Constants.ClientSend, epTfe)
    val monorailSr = Annotation(2L, Constants.ServerRecv, epMonorail)
    val monorailSs = Annotation(3L, Constants.ServerSend, epMonorail)
    val unicornCr  = Annotation(4L, Constants.ClientRecv, epTfe)
    val goodSpan = Span(1, "friendships/create", 12345L, None, List(unicornCs, monorailSr, monorailSs, unicornCr))
    val goodTrace = new Trace(Seq(goodSpan))

    assert(adjuster.adjust(goodTrace) === goodTrace)
  }

  test("adjust live case") {
    val epTfe = Some(Endpoint(123, 123, "tfe"))
    val epMonorail = Some(Endpoint(456, 456, "monorail"))

    val rootSr     = Annotation(1330539326400951L, Constants.ServerRecv, epTfe)
    val rootSs     = Annotation(1330539327264251L, Constants.ServerSend, epTfe)
    val spanTfe    = Span(1, "post", 7264365917420400007L, None, List(rootSr, rootSs))

    val unicornCs  = Annotation(1330539326401999L, Constants.ClientSend, epTfe)
    val monorailSr = Annotation(1330539325900366L, Constants.ServerRecv, epMonorail)
    val monorailSs = Annotation(1330539326524407L, Constants.ServerSend, epMonorail)
    val unicornCr  = Annotation(1330539327263984L, Constants.ClientRecv, epTfe)
    val spanMonorailUnicorn = Span(1, "friendships/create", 6379677665629798877L, Some(7264365917420400007L), List(unicornCs, monorailSr, monorailSs, unicornCr))

    val adjustedMonorailSr = Annotation(1330539326520971L, Constants.ServerRecv, epMonorail)
    val adjustedMonorailSs = Annotation(1330539327145012L, Constants.ServerSend, epMonorail)
    val spanAdjustedMonorail = Span(1, "friendships/create", 6379677665629798877L, Some(7264365917420400007L), List(unicornCs, adjustedMonorailSr, adjustedMonorailSs, unicornCr))

    val realTrace = new Trace(Seq(spanTfe, spanMonorailUnicorn))
    val expectedAdjustedTrace = new Trace(Seq(spanTfe, spanAdjustedMonorail))

    val adjusted = adjuster.adjust(realTrace)

    val adjustedSpans = adjusted.spans
    val expectedSpans = expectedAdjustedTrace.spans

    assert(expectedSpans.length === adjustedSpans.length)
    assert(adjustedSpans.length === adjustedSpans.intersect(expectedSpans).length)
  }

  test("adjust trace with depth 3") {
    val epTfe         = Some(Endpoint(123, 123, "tfe"))
    val epPassbird    = Some(Endpoint(456, 456, "passbird"))
    val epGizmoduck   = Some(Endpoint(789, 789, "gizmoduck"))

    val tfeSr         = Annotation(1330647964054410L, Constants.ServerRecv, epTfe)
    val tfeSs         = Annotation(1330647964057394L, Constants.ServerSend, epTfe)
    val spanTfe       = Span(1, "get", 583798990668970003L, None, List(tfeSr, tfeSs))

    val tfeCs         = Annotation(1330647964054881L, Constants.ClientSend, epTfe)
    val passbirdSr    = Annotation(1330647964055250L, Constants.ServerRecv, epPassbird)
    val passbirdSs    = Annotation(1330647964057394L, Constants.ServerSend, epPassbird)
    val tfeCr         = Annotation(1330647964057764L, Constants.ClientRecv, epTfe)
    val spanPassbird  = Span(1, "get_user_by_auth_token", 7625434200987291951L, Some(583798990668970003L), List(tfeCs, passbirdSr, passbirdSs, tfeCr))

    // Gizmoduck server entries are missing
    val passbirdCs    = Annotation(1330647964055324L, Constants.ClientSend, epPassbird)
    val passbirdCr    = Annotation(1330647964057127L, Constants.ClientRecv, epPassbird)
    val spanGizmoduck = Span(1, "get_by_auth_token", 119310086840195752L, Some(7625434200987291951L), List(passbirdCs, passbirdCr))

    val gizmoduckCs   = Annotation(1330647963542175L, Constants.ClientSend, epGizmoduck)
    val gizmoduckCr   = Annotation(1330647963542565L, Constants.ClientRecv, epGizmoduck)
    val spanMemcache  = Span(1, "get", 3983355768376203472L, Some(119310086840195752L), List(gizmoduckCs, gizmoduckCr))

    // Adjusted/created annotations
    val createdGizmoduckSr   = Annotation(1330647964055324L, Constants.ServerRecv, epGizmoduck)
    val createdGizmoduckSs   = Annotation(1330647964057127L, Constants.ServerSend, epGizmoduck)
    val adjustedGizmoduckCs  = Annotation(1330647964056030L, Constants.ClientSend, epGizmoduck)
    val adjustedGizmoduckCr = Annotation(1330647964056420L, Constants.ClientRecv, epGizmoduck)

    val spanAdjustedGizmoduck = Span(1, "get_by_auth_token", 119310086840195752L, Some(7625434200987291951L), List(passbirdCs, passbirdCr, createdGizmoduckSr, createdGizmoduckSs))
    val spanAdjustedMemcache = Span(1, "get", 3983355768376203472L, Some(119310086840195752L), List(adjustedGizmoduckCs, adjustedGizmoduckCr))

    val realTrace = new Trace(Seq(spanTfe, spanPassbird, spanGizmoduck, spanMemcache))
    val adjustedTrace = new Trace(Seq(spanTfe, spanPassbird, spanAdjustedGizmoduck, spanAdjustedMemcache))

    assert(adjustedTrace === adjuster.adjust(realTrace))
  }

  val ep1 = Some(Endpoint(1, 1, "ep1"))
  val ep2 = Some(Endpoint(2, 2, "ep2"))

  test("not adjust trace if invalid span") {
    val cs    = Annotation(1L, Constants.ClientSend, ep1)
    val sr = Annotation(10L, Constants.ServerRecv, ep2)
    val ss = Annotation(11L, Constants.ServerSend, ep2)
    val cr    = Annotation(4L, Constants.ClientRecv, ep1)
    val cr2    = Annotation(5L, Constants.ClientRecv, ep1)
    val spanBad   = Span(1, "method", 123L, None, List(cs, sr, ss, cr, cr2))
    val spanGood   = Span(1, "method", 123L, None, List(cs, sr, ss, cr))

    val trace1 = new Trace(Seq(spanGood))
    assert(trace1 != adjuster.adjust(trace1))

    val trace2 = new Trace(Seq(spanBad))
    assert(trace2 != adjuster.adjust(trace2))

  }

  test("not adjust trace if child longer than parent") {
    val cs = Annotation(1L, Constants.ClientSend, ep1)
    val sr = Annotation(2L, Constants.ServerRecv, ep2)
    val ss = Annotation(11L, Constants.ServerSend, ep2)
    val cr = Annotation(4L, Constants.ClientRecv, ep1)

    val span = Span(1, "method", 123L, None, List(cs, sr, ss, cr))

    val trace1 = new Trace(Seq(span))
    assert(trace1 === adjuster.adjust(trace1))
  }

  test("adjust even if we only have client send") {
    val tfeService = Endpoint(123, 9455, "api.twitter.com-ssl")

    val tfe = Span(142224153997690008L, "get", 142224153997690008L, None, List(
      Annotation(60498165L, Constants.ServerRecv, Some(tfeService)),
      Annotation(61031100L, Constants.ServerSend, Some(tfeService))
    ))

    val monorailService = Endpoint(456, 8000, "monorail")
    val clusterTwitterweb = Endpoint(123, -13145, "cluster_twitterweb_unicorn")

    val monorail = Span(142224153997690008L, "following/index", 7899774722699781565L, Some(142224153997690008L), List(
      Annotation(59501663L, Constants.ServerRecv, Some(monorailService)),
      Annotation(59934508L, Constants.ServerSend, Some(monorailService)),
      Annotation(60499730L, Constants.ClientSend, Some(clusterTwitterweb)),
      Annotation(61030844L, Constants.ClientRecv, Some(clusterTwitterweb))
    ))

    val tflockService = Endpoint(456, -14238, "tflock")
    val flockdbEdgesService = Endpoint(789, 6915, "flockdb_edges")

    val tflock = Span(142224153997690008L, "select", 6924056367845423617L, Some(7899774722699781565L), List(
      Annotation(59541848L, Constants.ClientSend, Some(tflockService)),
      Annotation(59544889L, Constants.ClientRecv, Some(tflockService)),
      Annotation(59541031L, Constants.ServerRecv, Some(flockdbEdgesService)),
      Annotation(59542894L, Constants.ServerSend, Some(flockdbEdgesService))
    ))

    val flockService = Endpoint(2130706433, 0, "flock")

    val flock = Span(142224153997690008L, "select", 7330066031642813936L, Some(6924056367845423617L), List(
      Annotation(59541299L, Constants.ClientSend, Some(flockService)),
      Annotation(59542778L, Constants.ClientRecv, Some(flockService))
    ))

    val trace = new Trace(Seq(monorail, tflock, tfe, flock))
    val adjusted = adjuster.adjust(trace)

    // let's see how we did
    val adjustedFlock = adjusted.getSpanById(7330066031642813936L).get
    val adjustedTflock = adjusted.getSpanById(6924056367845423617L).get
    val flockCs = adjustedFlock.getAnnotation(Constants.ClientSend).get
    val tflockSr = adjustedTflock.getAnnotation(Constants.ServerRecv).get

    // tflock must receive the request before it send a request to flock
    assert(flockCs.timestamp > tflockSr.timestamp)
  }
}
