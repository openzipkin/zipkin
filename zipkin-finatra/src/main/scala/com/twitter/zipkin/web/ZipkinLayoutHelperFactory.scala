package com.twitter.zipkin.web

import com.posterous.finatra.{LayoutHelper, LayoutHelperFactory}
import com.twitter.zipkin.config.ZipkinWebConfig

class ZipkinLayoutHelperFactory(config: ZipkinWebConfig) extends LayoutHelperFactory {
  class ZipkinLayoutHelper(yld: String) extends LayoutHelper(yld) {
    def rootUrl = config.rootUrl
  }

  override def apply(str: String) = {
    new ZipkinLayoutHelper(str)
  }
}
