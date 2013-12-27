package com.twitter.zipkin.common.json

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{ObjectMapper, SerializerProvider, JsonSerializer}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.twitter.zipkin.query.{TraceTimeline, TraceSummary, Trace}

/**
 * Indicates that the given subclass wraps a zipkin object in a more json-friendly manner
 */
trait WrappedJson

/**
 * Custom json generator that knows about zipkin datatypes
 */
object ZipkinJson {
  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  val module = new SimpleModule("ZipkinJson")

  // --- (SERIALIZERS) ---
  module.addSerializer(classOf[Trace], new ZipkinJsonSerializer(JsonTrace.wrap))
  module.addSerializer(classOf[TraceSummary], new ZipkinJsonSerializer(JsonTraceSummary.wrap))
  module.addSerializer(classOf[TraceTimeline], new ZipkinJsonSerializer(JsonTraceTimeline.wrap))

  mapper.registerModule(module)

  val writer = mapper.writer()

  def generate(obj: Any) = writer.writeValueAsString(obj)
}

class ZipkinJsonSerializer[T](wrap: T => WrappedJson) extends JsonSerializer[T] {
  def serialize(value: T, jgen: JsonGenerator, provider: SerializerProvider) {
    jgen.writeObject(wrap(value))
  }
}


