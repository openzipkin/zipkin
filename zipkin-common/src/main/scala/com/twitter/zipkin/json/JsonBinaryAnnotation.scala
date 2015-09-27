package com.twitter.zipkin.json

import java.nio.ByteBuffer

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.base.CaseFormat.{UPPER_CAMEL, UPPER_UNDERSCORE}
import com.google.common.io.BaseEncoding
import com.twitter.io.Charsets.Utf8
import com.twitter.zipkin.common.AnnotationType._
import com.twitter.zipkin.common.{AnnotationType, BinaryAnnotation}

case class JsonBinaryAnnotation(key: String,
                                value: Any,
                                @JsonProperty("type")
                                annotationType: Option[String],
                                endpoint: Option[JsonEndpoint])

object JsonBinaryAnnotation extends (BinaryAnnotation => JsonBinaryAnnotation) {
  val base64 = BaseEncoding.base64()
  val upperCamel = UPPER_UNDERSCORE.converterTo(UPPER_CAMEL)

  def apply(b: BinaryAnnotation) = {
    val (annotationType: Option[String], value: Any) = try {
      b.annotationType.value match {
        case Bool.value => (None, if (b.value.get() != 0) true else false)
        case Bytes.value => (Some("BYTES"), base64.encode(b.value.array(), b.value.position(), b.value.remaining()))
        case I16.value => (Some("I16"), b.value.getShort)
        case I32.value => (None, b.value.getInt)
        case I64.value => (Some("I64"), b.value.getLong)
        case Double.value => (None, b.value.getDouble)
        case String.value => (None, new String(b.value.array(), b.value.position(), b.value.remaining(), Utf8))
        case _ => throw new Exception("Unsupported annotation type: %s".format(b))
      }
    } catch {
      case e: Exception => "Error parsing binary annotation: %s".format(exceptionString(e))
    }
    JsonBinaryAnnotation(b.key, value, annotationType, b.host.map(JsonService))
  }

  def invert(b: JsonBinaryAnnotation) = {
    val annotationType = b.annotationType
      .map(upperCamel.convert(_))
      .map(AnnotationType.fromName(_))
      .getOrElse(b.value match {
      // annotationType is mostly redundant in json, especially as most annotations are strings
      // knowing this is json, the only way this won't map is list or map
      case bool: Boolean => Bool
      // Jackson defaults floats to doubles, and zipkin thrift doesn't have a float type
      case double: Double => Double
      case string: String => String
      case number: Number => I32 // default numeric is I32
      case _ => throw new IllegalArgumentException("Unsupported json annotation type: %s".format(b))
    })

    val bytes: ByteBuffer = try {
      annotationType.value match {
        case Bool.value => ByteBuffer.wrap(if (b.value.asInstanceOf[Boolean]) Array(1.toByte) else Array(0.toByte))
        case Bytes.value => ByteBuffer.wrap(base64.decode(b.value.asInstanceOf[String]))
        case I16.value => ByteBuffer.allocate(2).putShort(0, b.value.asInstanceOf[Short])
        case I32.value => ByteBuffer.allocate(4).putInt(0, b.value.asInstanceOf[Int])
        case I64.value => ByteBuffer.allocate(8).putLong(0, b.value.asInstanceOf[Long])
        case Double.value => ByteBuffer.allocate(8).putDouble(0, b.value.asInstanceOf[Double])
        case String.value => ByteBuffer.wrap(b.value.asInstanceOf[String].getBytes(Utf8))
        case _ => throw new IllegalArgumentException("Unsupported annotation type: %s".format(b))
      }
    } catch {
      case e: Exception => ByteBuffer.wrap("Error parsing json binary annotation: %s".format(exceptionString(e)).getBytes(Utf8))
    }
    new BinaryAnnotation(b.key, bytes, annotationType, b.endpoint.map(JsonService.invert))
  }

  private[this] def exceptionString(e: Exception) =
    "%s(%s)".format(e.getClass.getSimpleName, if (e.getMessage == null) "" else e.getMessage)
}