package com.twitter.zipkin.builder

import com.twitter.io.TempFile
import com.twitter.util.Eval
import com.twitter.zipkin.web.ZipkinWeb
import org.specs.Specification

class WebBuilderSpec extends Specification {
  "web builders" should {
    val eval = new Eval

    "compile" in {
      val builders = Seq(
        "/web-builder.scala",
        "/web-zk.scala"
      ) map { TempFile.fromResourcePath(_) }

      for (file <- builders) {
        file.getName() in {
          val b = eval[Builder[ZipkinWeb]](file)
          b.apply()
        }
      }
    }
  }
}
