/*
 * Copyright 2012 Twitter Inc.
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
package com.twitter.zipkin.sampler

import com.twitter.finagle.Service
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.common._
import org.scalatest.FunSuite

class SpanSamplerFilterTest extends FunSuite {
  test("filters spans based on their traceId") {
    val spans = Seq(
      Span(0, "svc", 123L),
      Span(1, "svc", 123L),
      Span(2, "svc", 123L),
      Span(3, "svc", 123L),
      Span(4, "svc", 123L))

    var rcvdSpans = Seq.empty[Span]

    val svc = new SpanSamplerFilter(_ > 2) andThen Service.mk[Seq[Span], Unit] { spans =>
      rcvdSpans = spans
      Future.Done
    }

    Await.ready(svc(spans))

    assert(rcvdSpans === spans.drop(3))
  }

  test("will not filter debug spans") {
    val spans = Seq(
      Span(0, "svc", 123L, debug = Some(true)),
      Span(1, "svc", 123L, debug = Some(true)),
      Span(1, "svc", 123L))

    var rcvdSpans = Seq.empty[Span]

    val svc = new SpanSamplerFilter(_  => false) andThen Service.mk[Seq[Span], Unit] { spans =>
      rcvdSpans = spans
      Future.Done
    }

    Await.ready(svc(spans))

    assert(rcvdSpans === spans.take(2))
  }
}
