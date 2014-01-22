/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.sampler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{HttpMuxer, Request, Response}
import com.twitter.util.{Extractable, Future, Var}
import org.jboss.netty.handler.codec.http.HttpMethod

class HttpVar(name: String, default: Double = 1.0) {
  private[this] val underlying = Var(default)

  val self: Var[Double] with Extractable[Double] = underlying

  HttpMuxer.addRichHandler("/vars/"+name, Service.mk[Request, Response] {
    case req if req.method == HttpMethod.GET =>
      req.response.contentString = underlying().toString
      Future.value(req.response)

    case req if req.method == HttpMethod.POST =>
      val rep = req.response
      req.params.get("value") match {
        case Some(value) =>
          try {
            val newRate = value.toDouble
            if (newRate > 1 || newRate < 0) {
              rep.statusCode = 400
              rep.contentString = "invalid rate"
            } else {
              underlying.update(newRate)
              rep.contentString = newRate.toString
            }
          } catch {
            case e: Exception =>
              rep.statusCode = 500
              rep.contentString = e.toString
          }
        case None =>
          rep.statusCode = 404
      }
      Future.value(rep)

    case req =>
      req.response.statusCode = 404
      Future.value(req.response)
  })
}
