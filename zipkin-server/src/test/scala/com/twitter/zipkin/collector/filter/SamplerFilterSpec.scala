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
import com.twitter.finagle.Service
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.collector.sampler.{EverythingGlobalSampler, NullGlobalSampler}
import org.specs.Specification
import org.specs.mock.{JMocker, ClassMocker}

class SamplerFilterSpec extends Specification with JMocker with ClassMocker {

  "SamplerFilter" should {
    val mockService = mock[Service[Span, Unit]]

    "let the span pass if debug flag is set" in {
      val span = Span(12345, "methodcall", 666, None, List(), Nil, true)
      val samplerProcessor = new SamplerFilter(NullGlobalSampler)

      expect {
        one(mockService).apply(span)
      }

      samplerProcessor(span, mockService)
    }

    "let the span pass if debug flag false and sampler says yes" in {
      val span = Span(12345, "methodcall", 666, None, List(), Nil, false)
      val samplerProcessor = new SamplerFilter(EverythingGlobalSampler)

      expect {
        one(mockService).apply(span)
      }

      samplerProcessor(span, mockService)
    }

    "don't let the span pass if debug flag false and sampler says no" in {
      val span = Span(12345, "methodcall", 666, None, List(), Nil, false)
      val samplerProcessor = new SamplerFilter(NullGlobalSampler)

      expect {}

      samplerProcessor(span, mockService)
    }
  }
}
