package com.twitter.zipkin.query.adjusters

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
import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}
import scala.collection._
import com.twitter.zipkin.gen
import com.twitter.zipkin.common.{Endpoint, Annotation, Span}
import com.twitter.zipkin.query.Trace

class TimeSkewAdjusterSpec extends Specification with JMocker with ClassMocker {
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
  val skewAnn1 = Annotation(100, gen.Constants.CLIENT_SEND, endpoint1)
  val skewAnn2 = Annotation(95, gen.Constants.SERVER_RECV, endpoint2) // skewed
  val skewAnn3 = Annotation(120, gen.Constants.SERVER_SEND, endpoint2) // skewed
  val skewAnn4 = Annotation(135, gen.Constants.CLIENT_RECV, endpoint1)
  val skewSpan1 = Span(1, "method1", 666, None,
    List(skewAnn1, skewAnn2, skewAnn3, skewAnn4), Nil)

  val skewAnn5 = Annotation(100, gen.Constants.CLIENT_SEND, endpoint2) // skewed
  val skewAnn6 = Annotation(115, gen.Constants.SERVER_RECV, endpoint3)
  val skewAnn7 = Annotation(120, gen.Constants.SERVER_SEND, endpoint3)
  val skewAnn8 = Annotation(115, gen.Constants.CLIENT_RECV, endpoint2) // skewed
  val skewSpan2 = Span(1, "method2", 777, Some(666),
    List(skewAnn5, skewAnn6, skewAnn7, skewAnn8), Nil)

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
  val expectedAnn1 = Annotation(100, gen.Constants.CLIENT_SEND, endpoint1)
  val expectedAnn2 = Annotation(105, gen.Constants.SERVER_RECV, endpoint2)
  val expectedAnn3 = Annotation(130, gen.Constants.SERVER_SEND, endpoint2)
  val expectedAnn4 = Annotation(135, gen.Constants.CLIENT_RECV, endpoint1)
  val expectedSpan1 = Span(1, "method1", 666, None,
    List(expectedAnn1, expectedAnn2, expectedAnn3, expectedAnn4), Nil)

  val expectedAnn5 = Annotation(110, gen.Constants.CLIENT_SEND, endpoint2)
  val expectedAnn6 = Annotation(115, gen.Constants.SERVER_RECV, endpoint3)
  val expectedAnn7 = Annotation(120, gen.Constants.SERVER_SEND, endpoint3)
  val expectedAnn8 = Annotation(125, gen.Constants.CLIENT_RECV, endpoint2)
  val expectedSpan2 = Span(1, "method2", 777, Some(666),
    List(expectedAnn5, expectedAnn6, expectedAnn7, expectedAnn8), Nil)

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
  val incompleteAnn1 = Annotation(100, gen.Constants.CLIENT_SEND, endpoint1)
  val incompleteAnn4 = Annotation(135, gen.Constants.CLIENT_RECV, endpoint1)
  val incompleteSpan1 = Span(1, "method1", 666, None,
    List(incompleteAnn1, incompleteAnn4), Nil)

  val incompleteTrace = new Trace(List[Span](expectedSpan1))

  val epKoalabird = Some(Endpoint(123, 123, "koalabird-cuckoo"))
  val epCuckoo = Some(Endpoint(321, 321, "cuckoo.thrift"))
  val epCassie = Some(Endpoint(456, 456, "cassie"))

  // This is real trace data that currently is not handled well by the adjuster
  val ann1 = Annotation(0, gen.Constants.SERVER_RECV, epCuckoo) // the server recv is reported as before client send
  val ann2 = Annotation(1, gen.Constants.CLIENT_SEND, epKoalabird)
  val ann3 = Annotation(1, gen.Constants.CLIENT_SEND, epCassie)
  val ann3F = Annotation(0, gen.Constants.CLIENT_SEND, epCassie)
  val ann4 = Annotation(85, gen.Constants.SERVER_SEND, epCuckoo) // reported at the same time, ok
  val ann5 = Annotation(85, gen.Constants.CLIENT_RECV, epKoalabird)
  val ann6 = Annotation(87, gen.Constants.CLIENT_RECV, epCassie)
  val ann6F = Annotation(86, gen.Constants.CLIENT_RECV, epCassie)

  val span1a = Span(1, "ValuesFromSource", 2209720933601260005L, None,
    List(ann3, ann6), Nil)
  val span1aFixed = Span(1, "ValuesFromSource", 2209720933601260005L, None,
    List(ann3F, ann6F), Nil)
  val span1b = Span(1, "ValuesFromSource", 2209720933601260005L, None,
    List(ann1, ann4), Nil)
  // the above two spans are part of the same actual span

  val span2 = Span(1, "multiget_slice", -855543208864892776L, Some(2209720933601260005L),
    List(ann2, ann5), Nil)

  val realTrace = new Trace(List(span1a, span1b, span2)).mergeSpans
  val expectedRealTrace = new Trace(List(span1aFixed, span1b, span2)).mergeSpans

  val adjuster = new TimeSkewAdjuster

  "TimeSkewAdjuster" should {
    "adjust span time from machine with incorrect clock" in {
      val actualTrace = adjuster.adjust(inputTrace)
      expectedTrace mustEqual actualTrace
    }

    "not adjust when there is no clock skew" in {
      val actualTrace = adjuster.adjust(expectedTrace)
      expectedTrace mustEqual actualTrace
    }

    // this happens if the server in an rpc is not trace enabled
    "not adjust when there is no server spans" in {
      val actualTrace = adjuster.adjust(incompleteTrace)
      incompleteTrace mustEqual actualTrace
    }

    "not adjust when core annotations are fine" in {
      val epTfe = Some(Endpoint(123, 123, "tfe"))
      val epMonorail = Some(Endpoint(456, 456, "monorail"))

      val unicornCs  = Annotation(1L, gen.Constants.CLIENT_SEND, epTfe)
      val monorailSr = Annotation(2L, gen.Constants.SERVER_RECV, epMonorail)
      val monorailSs = Annotation(3L, gen.Constants.SERVER_SEND, epMonorail)
      val unicornCr  = Annotation(4L, gen.Constants.CLIENT_RECV, epTfe)
      val goodSpan = Span(1, "friendships/create", 12345L, None, List(unicornCs, monorailSr, monorailSs, unicornCr), Nil)
      val goodTrace = new Trace(Seq(goodSpan)).mergeSpans

      val actualTrace = adjuster.adjust(goodTrace)
      goodTrace mustEqual actualTrace
    }

    "adjust live case" in {
      val epTfe = Some(Endpoint(123, 123, "tfe"))
      val epMonorail = Some(Endpoint(456, 456, "monorail"))

      val rootSr     = Annotation(1330539326400951L, gen.Constants.SERVER_RECV, epTfe)
      val rootSs     = Annotation(1330539327264251L, gen.Constants.SERVER_SEND, epTfe)
      val spanTfe    = Span(1, "POST", 7264365917420400007L, None, List(rootSr, rootSs), Nil)

      val unicornCs  = Annotation(1330539326401999L, gen.Constants.CLIENT_SEND, epTfe)
      val monorailSr = Annotation(1330539325900366L, gen.Constants.SERVER_RECV, epMonorail)
      val monorailSs = Annotation(1330539326524407L, gen.Constants.SERVER_SEND, epMonorail)
      val unicornCr  = Annotation(1330539327263984L, gen.Constants.CLIENT_RECV, epTfe)
      val spanMonorailUnicorn = Span(1, "friendships/create", 6379677665629798877L, Some(7264365917420400007L), List(unicornCs, monorailSr, monorailSs, unicornCr), Nil)

      val adjustedMonorailSr = Annotation(1330539326520971L, gen.Constants.SERVER_RECV, epMonorail)
      val adjustedMonorailSs = Annotation(1330539327145012L, gen.Constants.SERVER_SEND, epMonorail)
      val spanAdjustedMonorail = Span(1, "friendships/create", 6379677665629798877L, Some(7264365917420400007L), List(unicornCs, adjustedMonorailSr, adjustedMonorailSs, unicornCr), Nil)

      val realTrace = new Trace(Seq(spanTfe, spanMonorailUnicorn)).mergeSpans
      val expectedAdjustedTrace = new Trace(Seq(spanTfe, spanAdjustedMonorail)).mergeSpans

      val adjusted = adjuster.adjust(realTrace)

      val adjustedSpans = adjusted.spans
      val expectedSpans = expectedAdjustedTrace.spans

      adjustedSpans.length mustEqual expectedSpans.length
      adjustedSpans.intersect(expectedSpans).length mustEqual adjustedSpans.length
    }

    "adjust trace with depth 3" in {
      val epTfe         = Some(Endpoint(123, 123, "tfe"))
      val epPassbird    = Some(Endpoint(456, 456, "passbird"))
      val epGizmoduck   = Some(Endpoint(789, 789, "gizmoduck"))

      val tfeSr         = Annotation(1330647964054410L, gen.Constants.SERVER_RECV, epTfe)
      val tfeSs         = Annotation(1330647964057394L, gen.Constants.SERVER_SEND, epTfe)
      val spanTfe       = Span(1, "GET", 583798990668970003L, None, List(tfeSr, tfeSs), Nil)

      val tfeCs         = Annotation(1330647964054881L, gen.Constants.CLIENT_SEND, epTfe)
      val passbirdSr    = Annotation(1330647964055250L, gen.Constants.SERVER_RECV, epPassbird)
      val passbirdSs    = Annotation(1330647964057394L, gen.Constants.SERVER_SEND, epPassbird)
      val tfeCr         = Annotation(1330647964057764L, gen.Constants.CLIENT_RECV, epTfe)
      val spanPassbird  = Span(1, "get_user_by_auth_token", 7625434200987291951L, Some(583798990668970003L), List(tfeCs, passbirdSr, passbirdSs, tfeCr), Nil)

      // Gizmoduck server entries are missing
      val passbirdCs    = Annotation(1330647964055324L, gen.Constants.CLIENT_SEND, epPassbird)
      val passbirdCr    = Annotation(1330647964057127L, gen.Constants.CLIENT_RECV, epPassbird)
      val spanGizmoduck = Span(1, "get_by_auth_token", 119310086840195752L, Some(7625434200987291951L), List(passbirdCs, passbirdCr), Nil)

      val gizmoduckCs   = Annotation(1330647963542175L, gen.Constants.CLIENT_SEND, epGizmoduck)
      val gizmoduckCr   = Annotation(1330647963542565L, gen.Constants.CLIENT_RECV, epGizmoduck)
      val spanMemcache  = Span(1, "Get", 3983355768376203472L, Some(119310086840195752L), List(gizmoduckCs, gizmoduckCr), Nil)

      // Adjusted/created annotations
      val createdGizmoduckSr   = Annotation(1330647964055324L, gen.Constants.SERVER_RECV, epGizmoduck)
      val createdGizmoduckSs   = Annotation(1330647964057127L, gen.Constants.SERVER_SEND, epGizmoduck)
      val adjustedGizmoduckCs  = Annotation(1330647964056030L, gen.Constants.CLIENT_SEND, epGizmoduck)
      val adjustedGizmoduckCr = Annotation(1330647964056420L, gen.Constants.CLIENT_RECV, epGizmoduck)

      val spanAdjustedGizmoduck = Span(1, "get_by_auth_token", 119310086840195752L, Some(7625434200987291951L), List(passbirdCs, passbirdCr, createdGizmoduckSr, createdGizmoduckSs), Nil)
      val spanAdjustedMemcache = Span(1, "Get", 3983355768376203472L, Some(119310086840195752L), List(adjustedGizmoduckCs, adjustedGizmoduckCr), Nil)

      val realTrace = new Trace(Seq(spanTfe, spanPassbird, spanGizmoduck, spanMemcache)).mergeSpans
      val adjustedTrace = new Trace(Seq(spanTfe, spanPassbird, spanAdjustedGizmoduck, spanAdjustedMemcache))

      adjuster.adjust(realTrace) mustEqual adjustedTrace
    }

    val ep1 = Some(Endpoint(1, 1, "ep1"))
    val ep2 = Some(Endpoint(2, 2, "ep2"))

    "not adjust trace if invalid span" in {
      val cs    = Annotation(1L, gen.Constants.CLIENT_SEND, ep1)
      val sr = Annotation(10L, gen.Constants.SERVER_RECV, ep2)
      val ss = Annotation(11L, gen.Constants.SERVER_SEND, ep2)
      val cr    = Annotation(4L, gen.Constants.CLIENT_RECV, ep1)
      val cr2    = Annotation(5L, gen.Constants.CLIENT_RECV, ep1)
      val spanBad   = Span(1, "method", 123L, None, List(cs, sr, ss, cr, cr2), Nil)
      val spanGood   = Span(1, "method", 123L, None, List(cs, sr, ss, cr), Nil)

      val trace1 = new Trace(Seq(spanGood))
      adjuster.adjust(trace1) mustNotEq trace1

      val trace2 = new Trace(Seq(spanBad))
      adjuster.adjust(trace2) mustNotEq trace2

    }

    "not adjust trace if child longer than parent" in {
      val cs = Annotation(1L, gen.Constants.CLIENT_SEND, ep1)
      val sr = Annotation(2L, gen.Constants.SERVER_RECV, ep2)
      val ss = Annotation(11L, gen.Constants.SERVER_SEND, ep2)
      val cr = Annotation(4L, gen.Constants.CLIENT_RECV, ep1)

      val span = Span(1, "method", 123L, None, List(cs, sr, ss, cr), Nil)

      val trace1 = new Trace(Seq(span))
      adjuster.adjust(trace1) mustEqual trace1
    }
  }
}
