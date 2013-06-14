package com.twitter.zipkin.conversions

import com.twitter.zipkin.common.{AnnotationType, BinaryAnnotation}
import java.nio.ByteBuffer
import org.specs.Specification
import com.twitter.zipkin.common.json._

class JsonSpec extends Specification {

  "JsonAdapter" should {
    "convert binary annotations" in {
      val key = "key"

      "bool" in {
        val trueAnnotation = BinaryAnnotation(key, ByteBuffer.wrap(Array[Byte](1)), AnnotationType.Bool, None)
        val falseAnnotation = BinaryAnnotation(key, ByteBuffer.wrap(Array[Byte](0)), AnnotationType.Bool, None)

        val trueConvert = JsonBinaryAnnotation.wrap(trueAnnotation)
        trueConvert.value must_== true

        val falseConvert = JsonBinaryAnnotation.wrap(falseAnnotation)
        falseConvert.value must_== false
      }

      "short" in {
        val ann = BinaryAnnotation(key, ByteBuffer.allocate(2).putShort(0, 5.toShort), AnnotationType.I16, None)
        val convert = JsonBinaryAnnotation.wrap(ann)
        convert.value must_== 5
      }

      "int" in {
        val ann = BinaryAnnotation(key, ByteBuffer.allocate(4).putInt(0, 6), AnnotationType.I32, None)
        val convert = JsonBinaryAnnotation.wrap(ann)
        convert.value must_== 6
      }

      "long" in {
        val ann = BinaryAnnotation(key, ByteBuffer.allocate(8).putLong(0, 99999999999L), AnnotationType.I64, None)
        val convert = JsonBinaryAnnotation.wrap(ann)
        convert.value must_== 99999999999L
      }

      "double" in {
        val ann = BinaryAnnotation(key, ByteBuffer.allocate(8).putDouble(0, 1.3496), AnnotationType.Double, None)
        val convert = JsonBinaryAnnotation.wrap(ann)
        convert.value must_== 1.3496
      }

      "string" in {
        val ann = BinaryAnnotation(key, ByteBuffer.wrap("HELLO!".getBytes), AnnotationType.String, None)
        val convert = JsonBinaryAnnotation.wrap(ann)
        convert.value must_== "HELLO!"
      }
    }
  }
}
