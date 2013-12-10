package com.twitter.zipkin.common.mustache

import com.github.mustachejava.DefaultMustacheFactory
import com.twitter.mustache.ScalaObjectHandler
import java.io.StringWriter
import collection.JavaConversions.mapAsJavaMap

object ZipkinMustache {
  import java.io.Reader
  val mf = new DefaultMustacheFactory() {
    override def getReader(rn: String): Reader = {
      // hack to get partials to work properly
      val name = if (rn.startsWith("public")) rn else "templates/" + rn
      super.getReader(name)
    }
  }
  //TODO: why isn't the scala handler coercing maps properly?
  //mf.setObjectHandler(new ScalaObjectHandler)

  def render(template: String, data: Map[String, Object]): String = {
    val mustache = mf.compile(template)
    val output = new StringWriter
    mustache.execute(output, mapAsJavaMap(data)).flush()
    output.toString
  }
}
