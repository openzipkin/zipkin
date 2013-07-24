/*
 * Copyright 2012 Twitter Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.zipkin.config

import com.twitter.io.TempFile
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.util.Eval
import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.collector.ZipkinCollector
import org.specs.Specification

class ConfigSpec extends Specification {
  "/config" should {
    val eval = new Eval

    "validate collector configs" in {
      val configFiles = Seq(
        "/collector-dev.scala",
        "/collector-anorm.scala"
      ) map { TempFile.fromResourcePath(_) }

      for (file <- configFiles) {
        file.getName() in {
          val config = eval[Builder[RuntimeEnvironment => ZipkinCollector]](file)
          config must notBeNull
          config.apply()
        }
      }
    }
  }
}
