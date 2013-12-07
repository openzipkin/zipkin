package com.twitter.zipkin.common.mustache

import com.github.mustachejava.DefaultMustacheFactory
import com.twitter.mustache.ScalaObjectHandler
import java.io.StringWriter
import collection.JavaConversions._

object ZipkinMustache {
  val mf = new DefaultMustacheFactory()
  //TODO: why isn't the scala handler coercing maps properly?
  //mf.setObjectHandler(new ScalaObjectHandler)

  def render(template: String, data: Map[String, Object]): String = {
    val mustache = mf.compile(template)
    val output = new StringWriter
    mustache.execute(output, asJavaMap(data)).flush()
    output.toString
  }
}
