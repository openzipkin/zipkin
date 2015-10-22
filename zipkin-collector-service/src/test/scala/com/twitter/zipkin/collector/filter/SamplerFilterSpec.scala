package com.twitter.zipkin.collector.filter

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

import com.twitter.zipkin.collector.sampler.{EverythingGlobalSampler, NullGlobalSampler}
import com.twitter.zipkin.common.Span
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSuite, Matchers}

class SamplerFilterSpec extends FunSuite with Matchers with MockitoSugar {

  test("let the span pass if debug flag is set") {
    val span = Span(12345, "methodcall", 666, None, List(), Seq(), Some(true))
    val samplerProcessor = new SamplerFilter(NullGlobalSampler)

    samplerProcessor(Seq(span)) should be (Seq(span))
  }

  test("let the span pass if debug flag false and sampler says yes") {
    val span = Span(12345, "methodcall", 666, None, List(), Seq(), Some(false))
    val samplerProcessor = new SamplerFilter(EverythingGlobalSampler)

    samplerProcessor(Seq(span)) should be (Seq(span))
  }

  test("not let the span pass if debug flag false and sampler says no") {
    val span = Span(12345, "methodcall", 666, None, List(), Seq(), Some(false))
    val samplerProcessor = new SamplerFilter(NullGlobalSampler)

    samplerProcessor(Seq(span)) should be (empty)
  }
}
