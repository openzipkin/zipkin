package com.twitter.zipkin.web.mustache

import java.io._

import com.github.mustachejava.DefaultMustacheFactory
import com.twitter.mustache.ScalaObjectHandler

class ZipkinMustache() {
  private[this] val mf = new DefaultMustacheFactory
  mf.setObjectHandler(new ScalaObjectHandler) // data is a scala map

  def render(template: String, data: Map[String, Object]): String = {
    val mustache = mf.compile(template)
    val output = new StringWriter
    mustache.execute(output, data).flush()
    output.toString
  }
}
