/*
 Copyright 2013 Twitter, Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.twitter.zipkin.storm

import com.twitter.bijection._
import com.twitter.bijection.Codec
import com.twitter.summingbird.batch.BatchID
import com.twitter.bijection.scrooge.BinaryScalaCodec
import com.twitter.zipkin.common.{Annotation, BinaryAnnotation, Span}
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.gen
import com.twitter.algebird.Monoid

object Serialization {
  val gen2span = new AbstractBijection[Span, gen.Span] {
    override def invert(g: gen.Span) = g.toSpan
    def apply(span: Span) = span.toThrift
  }
  val bytes2genspan = BinaryScalaCodec(gen.Span)
  implicit val bytes2spanInj: Injection[Span, Array[Byte]] = bytes2genspan compose gen2span

  implicit def kInj[T: Codec]: Injection[(T, BatchID), Array[Byte]] = {
    implicit val buf =
      Bufferable.viaInjection[(T, BatchID), (Array[Byte], Array[Byte])]
    Bufferable.injectionOf[(T, BatchID)]
  }

  implicit def vInj[V: Codec]: Injection[(BatchID, V), Array[Byte]] =
    Injection.connect[(BatchID, V), (V, BatchID), Array[Byte]]

  implicit val mapInj: Injection[Map[String, Long], Array[Byte]] =
    Bufferable.injectionOf[Map[String, Long]]

  implicit val spanMonoid: Monoid[Span] = new Monoid[Span] {
    val zero = Span(0, "zero", 0, None, Nil, Nil, true)
    val invalid = Span(0, "invalid", 0, None, Nil, Nil, true)

    def plus(l: Span, r: Span) = {
      if (l == zero || r == invalid)  r
      else if (r == zero || l == invalid) l
      else if (l.id == r.id) l.mergeSpan(r)
      else invalid
    }
  }
}
