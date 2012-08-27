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

import com.twitter.finatra.{Response, Controller, View, Request}
import com.twitter.logging.Logger
import com.twitter.util.Future
import com.twitter.zipkin.adapter.{JsonQueryAdapter, ThriftQueryAdapter}
import com.twitter.zipkin.gen
import com.twitter.zipkin.config.ZipkinWebConfig
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Calendar

/**
 * Application that handles ZipkinWeb routes
 * @param config ZipkinWebConfig
 * @param client Thrift client to ZipkinQuery
 */
class App(config: ZipkinWebConfig, client: gen.ZipkinQuery.FinagledClient) extends Controller(config.statsReceiver) {

  val log = Logger.get()
  val dateFormat = new SimpleDateFormat("MM-dd-yyyy")
  val timeFormat = new SimpleDateFormat("HH:mm:ss")
  def getDate = dateFormat.format(Calendar.getInstance().getTime)
  def getTime = timeFormat.format(Calendar.getInstance().getTime)

  /* Index page */
  get("/") { request =>
    render.view(wrapView(new IndexView(getDate, getTime))).toFuture
  }

  /* Trace page */
  get("/show/:id") { request =>
    render.view(wrapView(new ShowView(request.params("id")))).toFuture
  }

  /* Static page for render trace from JSON */
  get("/static") { request =>
    render.view(wrapView(new StaticView)).toFuture
  }

  /* Static page for render trace from JSON */
  get("/aggregates") { request =>
    render.view(wrapAggregatesView(new AggregatesView)).toFuture
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
              JsonQueryAdapter(ThriftQueryAdapter(summary))
            }
          }
        }
      }
    }.flatten.map(render.json(_))
  }

  /**
   * API: services
   * Returns the total list of services Zipkin is aware of
   */
  get("/api/services") { request =>
    log.debug("/api/services")
    client.getServiceNames().map { services =>
      render.json(services.toSeq.sorted)
    }
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
        render.json(spans.toSeq.sorted)
      }
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
        render.json(anns.toSeq.sorted)
      }
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
        render.json(anns.toSeq.sorted)
      }
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
      render.json(JsonQueryAdapter(combo))
    }
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
    client.getTraceTimeToLive(id).map(render.json(_))
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
        togglePinState(id, true).map(render.json(_))
      }
      case "false" => {
        togglePinState(id, false).map(render.json(_))
      }
      case _ => {
        render.status(400).body("Must be true or false").toFuture
      }
    }
  }

  private def withServiceName(request: Request)(f: String => Future[Response]): Future[Response] = {
    request.params.get("serviceName") match {
      case Some(s) => {
        f(s)
      }
      case None => {
        render.status(401).body("Invalid service name").toFuture
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

  private def wrapView(v: View) = new View {
    val template = "templates/layouts/application.mustache"
    val rootUrl = config.rootUrl
    val innerView: View = v
    val javascripts = config.jsConfig.resources
    val stylesheets = config.cssConfig.resources
    lazy val body = innerView.render
  }

  private def wrapAggregatesView(v: View) = new View {
  val template = "templates/layouts/application.mustache"
  val rootUrl = config.rootUrl
  val innerView: View = v
  val javascripts = config.jsConfig.aggregatesResources
  val stylesheets = config.cssConfig.aggregatesResources
  lazy val body = innerView.render
  }
}

class IndexView(val endDate: String, val endTime: String) extends View {
  val template = "templates/index.mustache"
  val inlineJs = "$(Zipkin.Application.Index.initialize());"
}

class ShowView(traceId: String) extends View {
  val template = "templates/show.mustache"
  val inlineJs = "$(Zipkin.Application.Show.initialize(\"" + traceId + "\"));"
}

class AggregatesView() extends View {
  val template = "templates/aggregates.mustache"
  val inlineJs = "$(Zipkin.Application.Aggregates.initialize());"
}

class StaticView extends View {
  val template = "templates/static.mustache"
  val inlineJs = "$(Zipkin.Application.Static.initialize());"
}
