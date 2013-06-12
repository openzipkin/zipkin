package com.twitter.zipkin.common.json

import com.codahale.jerkson.Json
import org.codehaus.jackson.map.annotate.JsonCachable
import com.twitter.zipkin.common.BinaryAnnotation
import com.fasterxml.jackson.databind.{SerializerProvider, JsonSerializer}
import com.fasterxml.jackson.core.{JsonGenerator=>JacksonGenerator}
import com.fasterxml.jackson.databind.module.SimpleModule

import com.twitter.zipkin.query.Trace

/**
 * Custom json generator that knows about zipkin datatypes
 */
object ZipkinJson extends Json {
  val module = new SimpleModule("ZipkinJson")
  // --- (SERIALIZERS) ---
  module.addSerializer(classOf[BinaryAnnotation], new ZipkinJsonSerializer[BinaryAnnotation])
  module.addSerializer(classOf[Trace], new ZipkinJsonSerializer[Trace])

  mapper.registerModule(module)

}

@JsonCachable
class ZipkinJsonSerializer[T](implicit wrap: T => WrappedJson) extends JsonSerializer[T]
{
  def serialize(value: T, jgen: JacksonGenerator, provider: SerializerProvider) {
    jgen.writeObject(wrap(value))
  }
}


