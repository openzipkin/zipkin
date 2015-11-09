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
package com.twitter.zipkin.common

import java.nio.ByteBuffer
import com.twitter.io.Charsets.Utf8

case class BinaryAnnotation(
  key: String,
  value: ByteBuffer,
  annotationType: AnnotationType,
  host: Option[Endpoint]
) {
  def serviceName = host.map(_.serviceName).getOrElse("unknown")
}

object BinaryAnnotation {
  def apply[V](key: String, value: BinaryAnnotationValue[V], host: Option[Endpoint]): BinaryAnnotation =
    BinaryAnnotation(key, value.encode, value.annotationType, host)
  def apply[V](key: String, value: V, host: Option[Endpoint])(implicit enc: BinaryAnnotationValueEncoder[V]): BinaryAnnotation =
    BinaryAnnotation(key, BinaryAnnotationValue(value), host)
}

case class BinaryAnnotationValue[V](self: V)(implicit  enc: BinaryAnnotationValueEncoder[V]) extends Proxy {
  val annotationType = enc.typ
  def encode: ByteBuffer = enc.encode(self)
}

case class BinaryAnnotationValueEncoder[V](typ: AnnotationType, encode: V => ByteBuffer)

object BinaryAnnotationValueEncoder {
  private def intoBuffer[V](z: Int, f: ByteBuffer => V => ByteBuffer): V => ByteBuffer = {v =>
    f(ByteBuffer.allocate(z))(v).rewind.asInstanceOf[ByteBuffer]
  }

  implicit val StringEncoder =
    BinaryAnnotationValueEncoder[String](AnnotationType.String, {v => ByteBuffer.wrap(v.getBytes(Utf8))})

  implicit  val BooleanEncoder =
    BinaryAnnotationValueEncoder[Boolean](AnnotationType.Bool, {v => ByteBuffer.wrap(Array((if (v) 1 else 0).toByte))})

  implicit val ShortEncoder =
    BinaryAnnotationValueEncoder[Short](AnnotationType.I16, intoBuffer(2, _.putShort))

  implicit val IntEncoder =
    BinaryAnnotationValueEncoder[Int](AnnotationType.I32, intoBuffer(4, _.putInt))

  implicit val LongEncoder =
    BinaryAnnotationValueEncoder[Long](AnnotationType.I64, intoBuffer(8, _.putLong))

  implicit val DoubleEncoder =
    BinaryAnnotationValueEncoder[Double](AnnotationType.Double, intoBuffer(8, _.putDouble))
}