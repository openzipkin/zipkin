package com.twitter.zipkin.json

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{JsonSerializer, SerializerProvider}
import com.twitter.finatra.json.modules.FinatraJacksonModule
import com.twitter.finatra.json.utils.CamelCasePropertyNamingStrategy
import com.twitter.zipkin.common._

object ZipkinJson {

  val module = new SimpleModule("ZipkinJson")
  .addSerializer(classOf[Endpoint], serializer(JsonServiceBijection))
  .addSerializer(classOf[Annotation], serializer(JsonAnnotationBijection))
  .addSerializer(classOf[BinaryAnnotation], serializer(JsonBinaryAnnotationBijection))
  .addSerializer(classOf[Span], serializer(JsonSpanBijection))

  val mapper = new FinatraJacksonModule { // eases transition to finatra
    override protected def additionalJacksonModules = Seq(module)

    // don't convert to snake case, as the rest of zipkin expects lower-camel
    override protected val propertyNamingStrategy = CamelCasePropertyNamingStrategy
  }.provideScalaObjectMapper(injector = null)


  private[this] def serializer[T, R](converter: (T) => R) = {
    new JsonSerializer[T] {
      def serialize(input: T, jgen: JsonGenerator, provider: SerializerProvider) {
        jgen.writeObject(converter.apply(input))
      }
    }
  }
}
