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

import com.twitter.logging.Logger
import com.twitter.zipkin.adapter.{JsonQueryAdapter, JsonAdapter, ThriftQueryAdapter, ThriftAdapter}
import com.twitter.zipkin.gen
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Calendar
import com.posterous.finatra.{Request, FinatraApp}
import com.twitter.util.{Duration, Future}
import com.twitter.conversions.time._

class App(client: gen.ZipkinQuery.FinagledClient) extends FinatraApp {

  val log = Logger.get()

  get("/") { request =>
    render(path = "index.mustache", exports = new IndexObject)
  }

  get("/show/:id") { request =>
    render(path = "show.mustache", exports = new ShowObject(request.params("id")))
  }

  get("/api/query") { request =>
    /* Get trace ids */
    val traceIds = QueryRequest(request) match {
      case r: SpanQueryRequest => {
        log.debug(r.toString)
        client.getTraceIdsBySpanName(r.serviceName, r.spanName, r.endTimestamp, r.limit, r.order)
      }
      case r: AnnotationQueryRequest => {
        log.debug(r.toString)
        client.getTraceIdsByAnnotation(r.serviceName, r.annotation, null, r.endTimestamp, r.limit, r.order)
      }
      case r: KeyValueAnnotationQueryRequest => {
        log.debug(r.toString)
        client.getTraceIdsByAnnotation(r.serviceName, r.key, ByteBuffer.wrap(r.value.getBytes), r.endTimestamp, r.limit, r.order)
      }
      case r: ServiceQueryRequest => {
        log.debug(r.toString)
        client.getTraceIdsByServiceName(r.serviceName, r.endTimestamp, r.limit, r.order)
      }
    }
    val adjusters = getAdjusters(request)

    traceIds.map { ids =>
      ids match {
        case Nil => {
          Future.value(Seq.empty)
        }
        case _ => {
          client.getTraceSummariesByIds(ids, adjusters).map {
            _.map { summary =>
              JsonAdapter(ThriftAdapter(summary))
            }
          }
        }
      }
    }.flatten.map(toJson(_)).flatten
  }

  get("/api/services") { request =>
    log.debug("/api/services")
    client.getServiceNames().map { services =>
      toJson(services.toSeq.sorted)
    }.flatten
  }

  get("/api/spans/:serviceName") { request =>
    log.debug("/api/spans/")
    client.getSpanNames(request.params("serviceName")).map { spans =>
      toJson(spans.toSeq.sorted)
    }.flatten
  }

  get("/api/top_annotations/:serviceName") { request =>
    client.getTopAnnotations(request.params("serviceName")).map { anns =>
      toJson(anns.toSeq.sorted)
    }.flatten
  }

  get("/api/top_kv_annotations/:serviceName") { request =>
    client.getTopKeyValueAnnotations(request.params("serviceName")).map { anns =>
      toJson(anns.toSeq.sorted)
    }.flatten
  }

  get("/api/get/:id") { request =>
    log.info("/api/get")
    val adjusters = getAdjusters(request)
    val ids = Seq(request.params("id").toLong)
    log.debug(ids.toString())

    client.getTraceCombosByIds(ids, adjusters).map { _.map { ThriftQueryAdapter(_) }.head }.map { combo =>
      toJson(JsonQueryAdapter(combo))
    }.flatten
  }

  get("/api/is_pinned/:id") { request =>
    val id = request.params("id").toLong
    client.getTraceTimeToLive(id).map(toJson(_)).flatten
  }

  post("/api/pin/:id/:state") { request =>
    val id = request.params("id").toLong
    request.params("state").toLowerCase match {
      case "true" => {
        togglePinState(id, true).map(toJson(_)).flatten
      }
      case "false" => {
        togglePinState(id, false).map(toJson(_)).flatten
      }
      case _ => {
        render(400, "Must be true or false")
      }
    }
  }

  private def togglePinState(traceId: Long, state: Boolean): Future[Boolean] = {
    val ttl = state match {
      case true => {
        Future.value(Globals.pinTtl)
      }
      case false => {
        client.getDataTimeToLive()
      }
    }
    ttl.map { t =>
      client.setTraceTimeToLive(traceId, t).map(Unit => state)
    }.flatten
  }

  private def getAdjusters(request: Request) = {
    request.params.get("adjust_clock_skew") match {
      case Some(flag) => {
        flag match {
          case "false" => Seq.empty[gen.Adjust]
          case _ => Seq(gen.Adjust.TimeSkew)
        }
      }
      case _ => {
        Seq.empty[gen.Adjust]
      }
    }
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

class ShowObject(traceId: String) extends ExportObject {
  val inlineJs = "$(Zipkin.Application.Show.initialize(\"" + traceId + "\"));"
}

object Globals {
  var rootUrl = "http://localhost/"
  val dateFormat = new SimpleDateFormat("MM-dd-yyyy")
  val timeFormat = new SimpleDateFormat("HH:mm:ss")
  val ttl: Duration = 30.days

  def getDate = dateFormat.format(Calendar.getInstance().getTime)
  def getTime = timeFormat.format(Calendar.getInstance().getTime)
  def pinTtl: Int = ttl.inSeconds
}
