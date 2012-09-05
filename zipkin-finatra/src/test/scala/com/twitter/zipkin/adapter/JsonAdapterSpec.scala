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
package com.twitter.zipkin.adapter

import com.twitter.zipkin.common.{AnnotationType, BinaryAnnotation}
import java.nio.ByteBuffer
import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}

class JsonAdapterSpec extends Specification with JMocker with ClassMocker {
  "JsonAdapter" should {
    "convert binary annotations" in {
      val key = "key"

      "bool" in {
        val trueAnnotation = BinaryAnnotation(key, ByteBuffer.wrap(Array[Byte](1)), AnnotationType.Bool, None)
        val falseAnnotation = BinaryAnnotation(key, ByteBuffer.wrap(Array[Byte](0)), AnnotationType.Bool, None)

        val trueConvert = JsonAdapter(trueAnnotation)
        trueConvert.value must_== true

        val falseConvert = JsonAdapter(falseAnnotation)
        falseConvert.value must_== false
      }

      "short" in {
        val ann = BinaryAnnotation(key, ByteBuffer.allocate(2).putShort(0, 5.toShort), AnnotationType.I16, None)
        val convert = JsonAdapter(ann)
        convert.value must_== 5
      }

      "int" in {
        val ann = BinaryAnnotation(key, ByteBuffer.allocate(4).putInt(0, 6), AnnotationType.I32, None)
        val convert = JsonAdapter(ann)
        convert.value must_== 6
      }

      "long" in {
        val ann = BinaryAnnotation(key, ByteBuffer.allocate(8).putLong(0, 99999999999L), AnnotationType.I64, None)
        val convert = JsonAdapter(ann)
        convert.value must_== 99999999999L
      }

      "double" in {
        val ann = BinaryAnnotation(key, ByteBuffer.allocate(8).putDouble(0, 1.3496), AnnotationType.Double, None)
        val convert = JsonAdapter(ann)
        convert.value must_== 1.3496
      }

      "string" in {
        val ann = BinaryAnnotation(key, ByteBuffer.wrap("HELLO!".getBytes), AnnotationType.String, None)
        val convert = JsonAdapter(ann)
        convert.value must_== "HELLO!"
      }
    }
  }
}
