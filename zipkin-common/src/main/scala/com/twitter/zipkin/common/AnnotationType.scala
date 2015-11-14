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

/**
 * A subset of thrift base types, except [[AnnotationType.Bytes]].
 */
object AnnotationType {
  /**
   * Set to 0x01 when key is [[com.twitter.zipkin.Constants.ClientAddr]] or [[com.twitter.zipkin.Constants.ServerAddr]].
   */
  val Bool    = AnnotationType(0, "Bool")
  /** No encoding, or type is unknown. */
  val Bytes   = AnnotationType(1, "Bytes")
  val I16     = AnnotationType(2, "I16")
  val I32     = AnnotationType(3, "I32")
  val I64     = AnnotationType(4, "I64")
  val Double  = AnnotationType(5, "Double")
  /** The only type zipkin v1 supports search against. */
  val String  = AnnotationType(6, "String")

  def fromInt(v:Int) = v match {
    case Bool.value   => Bool
    case Bytes.value  => Bytes
    case I16.value    => I16
    case I32.value    => I32
    case I64.value    => I64
    case Double.value => Double
    case String.value => String
    case _            => String /* Uh... */
  }

  def fromName(v:String) = v match {
    case Bool.name   => Bool
    case Bytes.name  => Bytes
    case I16.name    => I16
    case I32.name    => I32
    case I64.name    => I64
    case Double.name => Double
    case String.name => String
  }
}

case class AnnotationType(value: Int, name: String)
