package com.twitter.zipkin.common.mustache

import com.github.mustachejava.DefaultMustacheFactory
import java.io._
import collection.JavaConversions.mapAsJavaMap

class ZipkinMustache(templateRoot: String, cache: Boolean) {
  import java.io.Reader
  class ZipkinMustacheFactory extends DefaultMustacheFactory() {
    override def getReader(rn: String): Reader = {
      // hack to get partials to work properly
      val name = if (rn.startsWith("public")) rn else "templates/" + rn

      if (cache) super.getReader(name) else {
        val file = new File(templateRoot, name)
        new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF8"))
      }
    }

    def invalidateCaches() {
      mustacheCache.invalidateAll()
      templateCache.invalidateAll()
    }
  }
  private[this] val mf = new ZipkinMustacheFactory
  //TODO: why isn't the scala handler coercing maps properly?
  mf.setObjectHandler(new ScalaObjectHandler)

  def render(template: String, data: Map[String, Object]): String = {
    if (!cache) mf.invalidateCaches()

    val mustache = mf.compile(template)
    val output = new StringWriter
    mustache.execute(output, mapAsJavaMap(data)).flush()
    output.toString
  }
}
