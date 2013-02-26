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
package com.twitter.zipkin.builder

import com.twitter.conversions.time._
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.thrift.ThriftClientRequest
import com.twitter.ostrich.admin.RuntimeEnvironment
import com.twitter.util.Duration
import com.twitter.zipkin.gen
import com.twitter.zipkin.config.{CssConfig, JsConfig}
import com.twitter.zipkin.web.{Resource, ZipkinWeb, App}

case class WebBuilder(
  rootUrl: String,
  queryClientBuilder: Builder[ClientBuilder.Complete[ThriftClientRequest, Array[Byte]]],
  pinTtl: Duration = 30.days,
  resourcePathPrefix: String = "/public",
  serverBuilder: ZipkinServerBuilder = ZipkinServerBuilder(8080, 9902)
) extends Builder[RuntimeEnvironment => ZipkinWeb] {

  /* Map dirname to content type */
  private val resourceDirs: Map[String, String] = Map[String, String](
    "css"       -> "text/css",
    "img"       -> "image/png",
    "js"        -> "application/javascript",
    "templates" -> "text/plain"
  )

  def pinTtl(ttl: Duration)        : WebBuilder = copy(pinTtl = ttl)
  def resourcePathPrefix(p: String): WebBuilder = copy(resourcePathPrefix = p)

  def apply(): (RuntimeEnvironment) => ZipkinWeb = (runtime: RuntimeEnvironment) => {
    serverBuilder.apply().apply(runtime)

    val jsConfig = new JsConfig {
      override val pathPrefix = resourcePathPrefix
    }
    val cssConfig = new CssConfig {
      override val pathPrefix = resourcePathPrefix
    }
    val queryClient = queryClientBuilder.apply().build()
    val finagledClient = new gen.ZipkinQuery.FinagledClient(queryClient)
    val app = new App(rootUrl, pinTtl, jsConfig, cssConfig, finagledClient, serverBuilder.statsReceiver)

    val resource = new Resource(resourceDirs)

    new ZipkinWeb(app, resource, serverBuilder.serverPort, serverBuilder.tracerFactory, serverBuilder.exceptionMonitorFactory)
  }
}
