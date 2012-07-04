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
package com.twitter.zipkin.web

import com.posterous.finatra.FinatraApp
import java.text.SimpleDateFormat
import java.util.Calendar
import com.twitter.zipkin.gen

class App(client: gen.ZipkinQuery.FinagledClient) extends FinatraApp {

  get("/") { request =>
    render(path="index.mustache", exports = new IndexObject)
  }

//  post("/query") { request =>
//    println(request.params.get("span_name"))
//    toJson(Seq())
//  }

  get("/api/services") { request =>
    client.getServiceNames().map {
      toJson(_)
    }
  }

  get("/api/spans") { request =>
    toJson()
  }

  get("/api/top_annotations/:serviceName") { request =>

  }

  get("/api/top_kv_annotations/:serviceName") { request =>

  }

  get("/show") { request =>

  }

  get("/get_trace") { request =>

  }

  get("/is_pinned") { request =>

  }

  post("/pin") { request =>

  }
}

trait Attribute
trait ExportObject {
  def environment: Attribute = new Attribute { def production = false }
  def flash: Option[Attribute] = None
  val clockSkew: Boolean = true
}

class IndexObject extends ExportObject {
  val inlineJs = "$(Zipkin.Application.Index.initialize());"
  val endDate = Globals.getDate
  val endTime = Globals.getTime
}

class QueryObject extends ExportObject {

}

object Globals {
  var rootUrl = "http://localhost/"
  val dateFormat = new SimpleDateFormat("MM-dd-yyyy")
  val timeFormat = new SimpleDateFormat("HH:mm:ss")

  def getDate = dateFormat.format(Calendar.getInstance().getTime)
  def getTime = timeFormat.format(Calendar.getInstance().getTime)
}
