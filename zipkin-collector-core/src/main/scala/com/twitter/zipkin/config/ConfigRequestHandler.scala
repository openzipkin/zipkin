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

import com.sun.net.httpserver.HttpExchange
import com.twitter.ostrich.admin.CgiRequestHandler
import com.twitter.zipkin.config.sampler.AdjustableRateConfig

class ConfigRequestHandler(
  adjustable: AdjustableRateConfig
) extends CgiRequestHandler {
  def handle(exchange: HttpExchange, path: List[String], parameters: List[(String, String)]) {
    if (path.length != 2) {
      render("invalid command", exchange, 404)
    }

    val paramMap = Map(parameters:_*)

    exchange.getRequestMethod match {
      case "GET" =>
        render(adjustable.get.toString, exchange, 200)
      case "POST" =>
        paramMap.get("value") match {
          case Some(value) =>
            try {
              adjustable.set(value.toDouble)
              render("success", exchange, 200)
            } catch {
              case e =>
                render("invalid input", exchange, 500)
            }
          case None =>
            render("invalid command", exchange, 404)
        }
    }
  }
}
