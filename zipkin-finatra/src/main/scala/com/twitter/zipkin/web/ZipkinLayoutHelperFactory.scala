package com.twitter.zipkin.web

import com.posterous.finatra.{LayoutHelper, LayoutHelperFactory}

class ZipkinLayoutHelperFactory extends LayoutHelperFactory {
  override def apply(str: String) = {
    new ZipkinLayoutHelper(str)
  }
}

class ZipkinLayoutHelper(yld: String) extends LayoutHelper(yld) {

  def rootUrl = Globals.rootUrl
}
