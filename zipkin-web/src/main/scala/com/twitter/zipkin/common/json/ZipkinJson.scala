package com.twitter.zipkin.common.json

import com.codahale.jerkson.Json
import org.codehaus.jackson.map.annotate.JsonCachable
import com.twitter.zipkin.common.BinaryAnnotation
import com.fasterxml.jackson.databind.{SerializerProvider, JsonSerializer}
import com.fasterxml.jackson.core.{JsonGenerator=>JacksonGenerator}
import com.fasterxml.jackson.databind.module.SimpleModule

import com.twitter.zipkin.query.{TraceTimeline, TraceSummary, Trace}

/**
 * Indicates that the given subclass wraps a zipkin object in a more json-friendly manner
 */
trait WrappedJson

/**
 * Custom json generator that knows about zipkin datatypes
 */
object ZipkinJson extends Json {
  val module = new SimpleModule("ZipkinJson")

  // --- (SERIALIZERS) ---
  module.addSerializer(classOf[Trace], new ZipkinJsonSerializer(JsonTrace.wrap))
  module.addSerializer(classOf[TraceSummary], new ZipkinJsonSerializer(JsonTraceSummary.wrap))
  module.addSerializer(classOf[TraceTimeline], new ZipkinJsonSerializer(JsonTraceTimeline.wrap))

  mapper.registerModule(module)
}

@JsonCachable
class ZipkinJsonSerializer[T](wrap: T => WrappedJson) extends JsonSerializer[T]
{
  def serialize(value: T, jgen: JacksonGenerator, provider: SerializerProvider) {
    jgen.writeObject(wrap(value))
  }
}


