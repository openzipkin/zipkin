package com.twitter.zipkin.json

import java.nio.ByteBuffer

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.base.CaseFormat.{UPPER_CAMEL, UPPER_UNDERSCORE}
import com.google.common.io.BaseEncoding
import com.twitter.io.Charsets.Utf8
import com.twitter.zipkin.common._
import com.twitter.zipkin.common.AnnotationType._

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
        case Bool.value => (None, if (b.value.get(0) != 0) true else false)
        case Bytes.value => (Some("BYTES"), base64.encode(b.value.array(), b.value.position(), b.value.remaining()))
        case I16.value => (Some("I16"), b.value.getShort(0))
        case I32.value => (Some("I32"), b.value.getInt(0))
        case I64.value => (Some("I64"), b.value.getLong(0))
        case Double.value => (Some("DOUBLE"), b.value.getDouble(0))
        case String.value => (None, new String(b.value.array(), b.value.position(), b.value.remaining(), Utf8))
        case _ => throw new Exception("Unsupported annotation type: %s".format(b))
      }
    } catch {
      case e: Exception => "Error parsing binary annotation: %s".format(exceptionString(e))
    }
    JsonBinaryAnnotation(b.key, value, annotationType, b.host.map(JsonEndpoint))
  }

  def invert(b: JsonBinaryAnnotation) = {
    val annotationType = b.annotationType
      .map(upperCamel.convert(_))
      .map(AnnotationType.fromName(_))
      .getOrElse(b.value match {
      // The only json types that can be implicit are booleans and strings. Numbers vary in shape.
      case bool: Boolean => Bool
      case string: String => String
      case _ => throw new IllegalArgumentException("Unsupported json annotation type: %s".format(b))
    })

    val bytes: ByteBuffer = try {
      annotationType.value match {
        case Bool.value => BinaryAnnotationValue(b.value.asInstanceOf[Boolean]).encode
        case Bytes.value => ByteBuffer.wrap(base64.decode(b.value.asInstanceOf[String]))
        case I16.value => BinaryAnnotationValue(b.value.asInstanceOf[Short]).encode
        case I32.value => BinaryAnnotationValue(b.value.asInstanceOf[Int]).encode
        case I64.value => BinaryAnnotationValue(b.value.asInstanceOf[Long]).encode
        case Double.value => BinaryAnnotationValue(b.value.asInstanceOf[Double]).encode
        case String.value => BinaryAnnotationValue(b.value.asInstanceOf[String]).encode
        case _ => throw new IllegalArgumentException("Unsupported annotation type: %s".format(b))
      }
    } catch {
      case e: Exception => BinaryAnnotationValue("Error parsing json binary annotation: %s".format(exceptionString(e))).encode
    }
    new BinaryAnnotation(b.key, bytes, annotationType, b.endpoint.map(JsonEndpoint.invert))
  }

  private[this] def exceptionString(e: Exception) =
    "%s(%s)".format(e.getClass.getSimpleName, if (e.getMessage == null) "" else e.getMessage)
}
