package com.twitter.zipkin.json

import java.nio.ByteBuffer

import com.google.common.io.BaseEncoding
import com.twitter.zipkin.common.{Endpoint, AnnotationType, BinaryAnnotation}
import org.scalatest.{FunSuite, Matchers}

class JsonBijectionsTest extends FunSuite with Matchers {
  val endpoint = Endpoint((192 << 24 | 168 << 16 | 1), 9411, "zipkin-query")

  test("endpoint") {
    val convert = JsonEndpoint("zipkin-query", "192.168.0.1", Some(9411))

    assert(JsonServiceBijection(endpoint) == convert)
    JsonServiceBijection(JsonServiceBijection.invert(convert)) should be(convert)
  }

  test("endpoint without port") {
    val convert = JsonEndpoint("zipkin-query", "192.168.0.1", None)

    assert(JsonServiceBijection(endpoint.copy(port = 0)) == convert)
    JsonServiceBijection(JsonServiceBijection.invert(convert)) should be(convert)
  }

  test("bytes") {
    val array = Array[Byte](1, 2, 3, 4)
    val ann = BinaryAnnotation("key", ByteBuffer.wrap(array), AnnotationType.Bytes, None)
    val convert = JsonBinaryAnnotationBijection(ann)
    assert(convert.value === BaseEncoding.base64().encode(array))
    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(convert)) should be(convert)
  }

  test("bool") {
    val trueAnnotation = BinaryAnnotation("key", ByteBuffer.wrap(Array[Byte](1)), AnnotationType.Bool, None)
    val falseAnnotation = BinaryAnnotation("key", ByteBuffer.wrap(Array[Byte](0)), AnnotationType.Bool, None)

    val trueConvert = JsonBinaryAnnotationBijection(trueAnnotation)
    assert(trueConvert.value === true)
    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(trueConvert)) should be(trueConvert)

    val falseConvert = JsonBinaryAnnotationBijection(falseAnnotation)
    assert(falseConvert.value === false)
    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(falseConvert)) should be(falseConvert)
  }

  test("short") {
    val ann = BinaryAnnotation("key", ByteBuffer.allocate(2).putShort(0, 5.toShort), AnnotationType.I16, None)
    val convert = JsonBinaryAnnotationBijection(ann)
    assert(convert.value === 5)
    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(convert)) should be(convert)
  }

  test("int") {
    val ann = BinaryAnnotation("key", ByteBuffer.allocate(4).putInt(0, 6), AnnotationType.I32, None)
    val convert = JsonBinaryAnnotationBijection(ann)
    assert(convert.value === 6)
    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(convert)) should be(convert)
  }

  test("long") {
    val ann = BinaryAnnotation("key", ByteBuffer.allocate(8).putLong(0, 99999999999L), AnnotationType.I64, None)
    val convert = JsonBinaryAnnotationBijection(ann)
    assert(convert.value === 99999999999L)
    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(convert)) should be(convert)
  }

  test("double") {
    val ann = BinaryAnnotation("key", ByteBuffer.allocate(8).putDouble(0, 1.3496), AnnotationType.Double, None)
    val convert = JsonBinaryAnnotationBijection(ann)
    assert(convert.value === 1.3496)
    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(convert)) should be(convert)
  }

  test("string") {
    val ann = BinaryAnnotation("key", ByteBuffer.wrap("HELLO!".getBytes), AnnotationType.String, None)
    val convert = JsonBinaryAnnotationBijection(ann)
    assert(convert.value === "HELLO!")
    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(convert)) should be(convert)
  }

  test("boolean's annotation type is implicit") {
    val unqualified = JsonBinaryAnnotation("key", true, None, None)
    val qualified = JsonBinaryAnnotation("key", true, Some("BOOL"), None)

    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(qualified)) should be(unqualified)
    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(unqualified)) should be(unqualified)
  }

  test("int's annotation type is implicit") {
    val unqualified = JsonBinaryAnnotation("key", 6, None, None)
    val qualified = JsonBinaryAnnotation("key", 6, Some("I32"), None)

    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(qualified)) should be(unqualified)
    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(unqualified)) should be(unqualified)
  }

  test("double's annotation type is implicit") {
    val unqualified = JsonBinaryAnnotation("key", 6.0D, None, None)
    val qualified = JsonBinaryAnnotation("key", 6.0D, Some("DOUBLE"), None)

    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(qualified)) should be(unqualified)
    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(unqualified)) should be(unqualified)
  }

  test("string's annotation type is implicit") {
    val unqualified = JsonBinaryAnnotation("key", "HELLO!", None, None)
    val qualified = JsonBinaryAnnotation("key", "HELLO!", Some("STRING"), None)

    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(qualified)) should be(unqualified)
    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(unqualified)) should be(unqualified)
  }

  test("float coerses to double type in json") { // since there's no FLOAT type
    val unqualified = JsonBinaryAnnotation("key", 6.0, None, None)
    val qualified = JsonBinaryAnnotation("key", 6.0, Some("DOUBLE"), None)
    val double = JsonBinaryAnnotation("key", 6.0D, None, None)

    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(qualified)) should be(double)
    JsonBinaryAnnotationBijection(JsonBinaryAnnotationBijection.invert(unqualified)) should be(double)
  }
}
