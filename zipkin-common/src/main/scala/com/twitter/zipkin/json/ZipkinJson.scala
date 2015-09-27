package com.twitter.zipkin.json

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{ObjectMapper, JsonSerializer, SerializerProvider}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.zipkin.common._

object ZipkinJson {

  val module = new SimpleModule("ZipkinJson")
  .addSerializer(classOf[Endpoint], serializer(JsonService))
  .addSerializer(classOf[Annotation], serializer(JsonAnnotation))
  .addSerializer(classOf[BinaryAnnotation], serializer(JsonBinaryAnnotation))
  .addSerializer(classOf[Span], serializer(JsonSpan))

  val mapper = new ObjectMapper()
  .registerModule(new DefaultScalaModule())
  .registerModule(module)
  .setSerializationInclusion(JsonInclude.Include.NON_NULL)

  private[this] def serializer[T, R](converter: (T) => R) = {
    new JsonSerializer[T] {
      def serialize(input: T, jgen: JsonGenerator, provider: SerializerProvider) {
        jgen.writeObject(converter.apply(input))
      }
    }
  }
}
