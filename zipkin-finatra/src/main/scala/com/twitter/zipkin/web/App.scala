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

import com.posterous.finatra.{Request, FinatraApp}
import com.twitter.logging.Logger
import com.twitter.util.Future
import com.twitter.zipkin.adapter.{JsonQueryAdapter, JsonAdapter, ThriftQueryAdapter, ThriftAdapter}
import com.twitter.zipkin.gen
import com.twitter.zipkin.config.ZipkinWebConfig
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Calendar
import org.jboss.netty.handler.codec.http.HttpResponse

/**
 * Application that handles ZipkinWeb routes
 * @param config ZipkinWebConfig
 * @param client Thrift client to ZipkinQuery
 */
class App(config: ZipkinWebConfig, client: gen.ZipkinQuery.FinagledClient) extends FinatraApp {

  val log = Logger.get()
  val dateFormat = new SimpleDateFormat("MM-dd-yyyy")
  val timeFormat = new SimpleDateFormat("HH:mm:ss")
  def getDate = dateFormat.format(Calendar.getInstance().getTime)
  def getTime = timeFormat.format(Calendar.getInstance().getTime)

  /* Index page */
  get("/") { request =>
    render(path = "index.mustache", exports = new IndexObject(getDate, getTime))
  }

  /* Trace page */
  get("/show/:id") { request =>
    render(path = "show.mustache", exports = new ShowObject(request.params("id")))
  }

  /* Static page for render trace from JSON */
  get("/static") { request =>
    render(path = "static.mustache", exports = new StaticObject)
  }

  /**
   * API: query
   * Returns query results that satisfy the request parameters in order of descending duration
   *
   * Required GET params:
   * - service_name: String
   * - end_date: date String formatted to `QueryRequest.fmt`
   *
   * Optional GET params:
   * - limit: Int, default 100
   * - span_name: String
   * - time_annotation: String
   * - annotation_key, annotation_value: String
   * - adjust_clock_skew = (true|false), default true
   */
  get("/api/query") { request =>
    /* Get trace ids */
    val traceIds = QueryRequest(request) match {
      case r: SpanQueryRequest => {
        client.getTraceIdsBySpanName(r.serviceName, r.spanName, r.endTimestamp, r.limit, r.order)
      }
      case r: AnnotationQueryRequest => {
        client.getTraceIdsByAnnotation(r.serviceName, r.annotation, null, r.endTimestamp, r.limit, r.order)
      }
      case r: KeyValueAnnotationQueryRequest => {
        client.getTraceIdsByAnnotation(r.serviceName, r.key, ByteBuffer.wrap(r.value.getBytes), r.endTimestamp, r.limit, r.order)
      }
      case r: ServiceQueryRequest => {
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

  /**
   * API: services
   * Returns the total list of services Zipkin is aware of
   */
  get("/api/services") { request =>
    log.debug("/api/services")
    client.getServiceNames().map { services =>
      toJson(services.toSeq.sorted)
    }.flatten
  }

  /**
   * API: spans
   * Returns a list of spans for a particular service
   *
   * Required GET params:
   * - serviceName: String
   */
  get("/api/spans") { request =>
    log.debug("/api/spans")
    withServiceName(request) { serviceName =>
      client.getSpanNames(serviceName).map { spans =>
        toJson(spans.toSeq.sorted)
      }.flatten
    }
  }

  /**
   * API: top_annotations
   * Returns a list of top/popular time-based annotations for a particular service
   *
   * Required GET params:
   * - serviceName: string
   */
  get("/api/top_annotations") { request =>
    withServiceName(request) { serviceName =>
      client.getTopAnnotations(serviceName).map { anns =>
        toJson(anns.toSeq.sorted)
      }.flatten
    }
  }

  /**
   * API: top_kv_annotations
   * Returns a list of the top/popular keys for key-value annotations for a particular service
   *
   * Required GET params:
   * - serviceName: String
   */
  get("/api/top_kv_annotations") { request =>
    withServiceName(request) { serviceName =>
      client.getTopKeyValueAnnotations(serviceName).map { anns =>
        toJson(anns.toSeq.sorted)
      }.flatten
    }
  }

  /**
   * API: get
   * Returns the data for a particular trace
   *
   * Required GET params:
   * - id: Long
   *
   * Optional GET params:
   * - adjust_clock_skew: (true|false), default true
   */
  get("/api/get/:id") { request =>
    log.info("/api/get")
    val adjusters = getAdjusters(request)
    val ids = Seq(request.params("id").toLong)
    log.debug(ids.toString())

    client.getTraceCombosByIds(ids, adjusters).map { _.map { ThriftQueryAdapter(_) }.head }.map { combo =>
      toJson(JsonQueryAdapter(combo))
    }.flatten
  }

  /**
   * API: is_pinned
   * Returns whether a trace has been pinned
   *
   * Required GET params:
   * - id: Long
   */
  get("/api/is_pinned/:id") { request =>
    val id = request.params("id").toLong
    client.getTraceTimeToLive(id).map(toJson(_)).flatten
  }

  /**
   * API: pin
   * Pins a trace (sets its TTL)
   *
   * Required GET params:
   * - id: Long
   * - state: Boolean (true|false)
   */
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

  private def withServiceName(request: Request)(f: String => Future[HttpResponse]): Future[HttpResponse] = {
    request.params.get("serviceName") match {
      case Some(s) => {
        f(s)
      }
      case None => {
        render(401, "Invalid service name")
      }
    }
  }

  private def togglePinState(traceId: Long, state: Boolean): Future[Boolean] = {
    val ttl = state match {
      case true => {
        Future.value(config.pinTtl.inSeconds)
      }
      case false => {
        client.getDataTimeToLive()
      }
    }
    ttl.map { t =>
      client.setTraceTimeToLive(traceId, t).map(Unit => state)
    }.flatten
  }

  /**
   * Returns a sequence of adjusters based on the params for a request. Default is TimeSkewAdjuster
   */
  private def getAdjusters(request: Request) = {
    request.params.get("adjust_clock_skew") match {
      case Some(flag) => {
        flag match {
          case "false" => Seq.empty[gen.Adjust]
          case _ => Seq(gen.Adjust.TimeSkew)
        }
      }
      case _ => {
        Seq(gen.Adjust.TimeSkew)
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

class IndexObject(val endDate: String, val endTime: String) extends ExportObject {
  val inlineJs = "$(Zipkin.Application.Index.initialize());"
}

class ShowObject(traceId: String) extends ExportObject {
  val inlineJs = "$(Zipkin.Application.Show.initialize(\"" + traceId + "\"));"
}

class StaticObject extends ExportObject {
  val inlineJs = "$(Zipkin.Application.Static.initialize());"
}
