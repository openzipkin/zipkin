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

object AnnotationType {
  case object Bool extends AnnotationType(0, "Bool")
  case object Bytes extends AnnotationType(1, "Bytes")
  case object I16 extends AnnotationType(2, "I16")
  case object I32 extends AnnotationType(3, "I32")
  case object I64 extends AnnotationType(4, "I64")
  case object Double extends AnnotationType(5, "Double")
  case object String extends AnnotationType(6, "String")
}

case class AnnotationType(value: Int, name: String)
