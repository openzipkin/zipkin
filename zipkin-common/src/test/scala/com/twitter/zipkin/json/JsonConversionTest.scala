package com.twitter.zipkin.json

import java.nio.ByteBuffer

import com.google.common.io.BaseEncoding
import com.twitter.zipkin.common.{Endpoint, AnnotationType, BinaryAnnotation}
import org.scalatest.{FunSuite, Matchers}

class JsonConversionTest extends FunSuite with Matchers {

  test("span with null annotations, binaryAnnotations") {
    val convert = JsonSpan("0000000000000001", "GET", "0000000000003039", None, None, None, null, null)

    JsonSpan(JsonSpan.invert(convert)) should be(JsonSpan("0000000000000001", "get", "0000000000003039"))
  }

  test("span with ids less trailing zeros") {
    val convert = JsonSpan("0000001", "GET", "3039")

    JsonSpan(JsonSpan.invert(convert)) should be(JsonSpan("0000000000000001", "get", "0000000000003039"))
  }

  val endpoint = Endpoint((192 << 24 | 168 << 16 | 1), 9411, "zipkin-query")

  test("endpoint") {
    val convert = JsonEndpoint("zipkin-query", "192.168.0.1", Some(9411))

    assert(JsonEndpoint(endpoint) == convert)
    JsonEndpoint(JsonEndpoint.invert(convert)) should be(convert)
  }

  test("endpoint without port") {
    val convert = JsonEndpoint("zipkin-query", "192.168.0.1", None)

    assert(JsonEndpoint(endpoint.copy(port = 0)) == convert)
    JsonEndpoint(JsonEndpoint.invert(convert)) should be(convert)
  }

  test("bytes") {
    val array = Array[Byte](1, 2, 3, 4)
    val ann = BinaryAnnotation("key", ByteBuffer.wrap(array), AnnotationType.Bytes, None)
    val convert = JsonBinaryAnnotation(ann)
    assert(convert.value === BaseEncoding.base64().encode(array))
    assert(ann.value.position() === 0)
    JsonBinaryAnnotation(JsonBinaryAnnotation.invert(convert)) should be(convert)
  }

  test("bool") {
    val trueAnnotation = BinaryAnnotation("key", ByteBuffer.wrap(Array[Byte](1)), AnnotationType.Bool, None)
    val falseAnnotation = BinaryAnnotation("key", ByteBuffer.wrap(Array[Byte](0)), AnnotationType.Bool, None)

    val trueConvert = JsonBinaryAnnotation(trueAnnotation)
    assert(trueConvert.value === true)
    assert(trueAnnotation.value.position() === 0)
    JsonBinaryAnnotation(JsonBinaryAnnotation.invert(trueConvert)) should be(trueConvert)

    val falseConvert = JsonBinaryAnnotation(falseAnnotation)
    assert(falseConvert.value === false)
    assert(falseAnnotation.value.position() === 0)
    JsonBinaryAnnotation(JsonBinaryAnnotation.invert(falseConvert)) should be(falseConvert)
  }

  test("short") {
    val ann = BinaryAnnotation("key", ByteBuffer.allocate(2).putShort(0, 5.toShort), AnnotationType.I16, None)
    val convert = JsonBinaryAnnotation(ann)
    assert(convert.value === 5)
    assert(ann.value.position() === 0)
    JsonBinaryAnnotation(JsonBinaryAnnotation.invert(convert)) should be(convert)
  }

  test("int") {
    val ann = BinaryAnnotation("key", ByteBuffer.allocate(4).putInt(0, 6), AnnotationType.I32, None)
    val convert = JsonBinaryAnnotation(ann)
    assert(convert.value === 6)
    assert(ann.value.position() === 0)
    JsonBinaryAnnotation(JsonBinaryAnnotation.invert(convert)) should be(convert)
  }

  test("long") {
    val ann = BinaryAnnotation("key", ByteBuffer.allocate(8).putLong(0, 99999999999L), AnnotationType.I64, None)
    val convert = JsonBinaryAnnotation(ann)
    assert(convert.value === 99999999999L)
    assert(ann.value.position() === 0)
    JsonBinaryAnnotation(JsonBinaryAnnotation.invert(convert)) should be(convert)
  }

  test("double") {
    val ann = BinaryAnnotation("key", ByteBuffer.allocate(8).putDouble(0, 1.3496), AnnotationType.Double, None)
    val convert = JsonBinaryAnnotation(ann)
    assert(convert.value === 1.3496)
    assert(ann.value.position() === 0)
    JsonBinaryAnnotation(JsonBinaryAnnotation.invert(convert)) should be(convert)
  }

  test("string") {
    val ann = BinaryAnnotation("key", ByteBuffer.wrap("HELLO!".getBytes), AnnotationType.String, None)
    val convert = JsonBinaryAnnotation(ann)
    assert(convert.value === "HELLO!")
    assert(ann.value.position() === 0)
    JsonBinaryAnnotation(JsonBinaryAnnotation.invert(convert)) should be(convert)
  }

  test("boolean's annotation type is implicit") {
    val unqualified = JsonBinaryAnnotation("key", true, None, None)
    val qualified = JsonBinaryAnnotation("key", true, Some("BOOL"), None)

    JsonBinaryAnnotation(JsonBinaryAnnotation.invert(qualified)) should be(unqualified)
    JsonBinaryAnnotation(JsonBinaryAnnotation.invert(unqualified)) should be(unqualified)
  }

  test("string's annotation type is implicit") {
    val unqualified = JsonBinaryAnnotation("key", "HELLO!", None, None)
    val qualified = JsonBinaryAnnotation("key", "HELLO!", Some("STRING"), None)

    JsonBinaryAnnotation(JsonBinaryAnnotation.invert(qualified)) should be(unqualified)
    JsonBinaryAnnotation(JsonBinaryAnnotation.invert(unqualified)) should be(unqualified)
  }

  test("float coerses to double type in json") { // since there's no FLOAT type in the thrift
    val float = JsonBinaryAnnotation("key", 6.0, Some("DOUBLE"), None)
    val double = JsonBinaryAnnotation("key", 6.0D, Some("DOUBLE"), None)

    JsonBinaryAnnotation(JsonBinaryAnnotation.invert(float)) should be(double)
  }
}
