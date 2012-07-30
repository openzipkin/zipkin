package com.twitter.zipkin.collector.processor

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
import com.twitter.zipkin.gen
import com.twitter.zipkin.common.{Span, Endpoint, Annotation}
import com.twitter.ostrich.stats.{Histogram, Distribution, Stats}
import com.twitter.zipkin.collector.sampler.{EverythingGlobalSampler, NullGlobalSampler}

class SamplerProcessorFilterSpec extends Specification {

  "SamplerProcessorFilter" should {
    "let the span pass if debug flag is set" in {
      val span = Span(12345, "methodcall", 666, None, List(), Nil, true)
      val spans = Seq(span)
      val samplerProcessor = new SamplerProcessorFilter(NullGlobalSampler)
      samplerProcessor(spans) mustEqual spans
    }

    "let the span pass if debug flag false and sampler says yes" in {
      val span = Span(12345, "methodcall", 666, None, List(), Nil, false)
      val spans = Seq(span)
      val samplerProcessor = new SamplerProcessorFilter(EverythingGlobalSampler)
      samplerProcessor(spans) mustEqual spans
    }

    "don't let the span pass if debug flag false and sampler says no" in {
      val span = Span(12345, "methodcall", 666, None, List(), Nil, false)
      val spans = Seq(span)
      val samplerProcessor = new SamplerProcessorFilter(NullGlobalSampler)
      samplerProcessor(spans) mustEqual Seq()
    }
  }
}
