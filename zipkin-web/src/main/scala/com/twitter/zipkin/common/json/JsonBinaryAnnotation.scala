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
package com.twitter.zipkin.common.json

import com.twitter.zipkin.common.{BinaryAnnotation, AnnotationType}

case class JsonBinaryAnnotation(key: String,
                                value: Any,
                                annotationType: AnnotationType,
                                host: Option[JsonEndpoint])
  extends WrappedJson

object JsonBinaryAnnotation {
   def wrap(b: BinaryAnnotation) : JsonBinaryAnnotation = {
    val value = try {
      b.annotationType match {
        case AnnotationType(0, _) => if (b.value.get() != 0) true else false  // bool
        case AnnotationType(1, _) => new String(b.value.array(), b.value.position(), b.value.remaining()) // bytes
        case AnnotationType(2, _) => b.value.getShort            // i16
        case AnnotationType(3, _) => b.value.getInt              // i32
        case AnnotationType(4, _) => b.value.getLong             // i64
        case AnnotationType(5, _) => b.value.getDouble           // double
        case AnnotationType(6, _) => new String(b.value.array(), b.value.position(), b.value.remaining()) // string
        case _ => {
          throw new Exception("Unsupported annotation type: %s".format(b))
        }
      }
    } catch {
      case e: Exception => "Error parsing binary annotation"
    }
    JsonBinaryAnnotation(b.key, value, b.annotationType, b.host.map(JsonEndpoint.wrap(_)))
  }
}

