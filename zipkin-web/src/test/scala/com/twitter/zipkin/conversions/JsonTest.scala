package com.twitter.zipkin.conversions

import com.twitter.zipkin.common.json._
import com.twitter.zipkin.common.{AnnotationType, BinaryAnnotation}
import java.nio.ByteBuffer
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class JsonTest extends FunSuite {
  val key = "key"
  test("bool") {
    val trueAnnotation = BinaryAnnotation(key, ByteBuffer.wrap(Array[Byte](1)), AnnotationType.Bool, None)
    val falseAnnotation = BinaryAnnotation(key, ByteBuffer.wrap(Array[Byte](0)), AnnotationType.Bool, None)

    val trueConvert = JsonBinaryAnnotation.wrap(trueAnnotation)
    assert(trueConvert.value === true)

    val falseConvert = JsonBinaryAnnotation.wrap(falseAnnotation)
    assert(falseConvert.value === false)
  }

  test("short") {
    val ann = BinaryAnnotation(key, ByteBuffer.allocate(2).putShort(0, 5.toShort), AnnotationType.I16, None)
    val convert = JsonBinaryAnnotation.wrap(ann)
    assert(convert.value === 5)
  }

  test("int") {
    val ann = BinaryAnnotation(key, ByteBuffer.allocate(4).putInt(0, 6), AnnotationType.I32, None)
    val convert = JsonBinaryAnnotation.wrap(ann)
    assert(convert.value === 6)
  }

  test("long") {
    val ann = BinaryAnnotation(key, ByteBuffer.allocate(8).putLong(0, 99999999999L), AnnotationType.I64, None)
    val convert = JsonBinaryAnnotation.wrap(ann)
    assert(convert.value === 99999999999L)
  }

  test("double") {
    val ann = BinaryAnnotation(key, ByteBuffer.allocate(8).putDouble(0, 1.3496), AnnotationType.Double, None)
    val convert = JsonBinaryAnnotation.wrap(ann)
    assert(convert.value === 1.3496)
  }

  test("string") {
    val ann = BinaryAnnotation(key, ByteBuffer.wrap("HELLO!".getBytes), AnnotationType.String, None)
    val convert = JsonBinaryAnnotation.wrap(ann)
    assert(convert.value === "HELLO!")
  }
}
