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

import com.twitter.finagle.tracing.SpanId
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finatra.{Response, Controller, View, Request}
import com.twitter.logging.Logger
import com.twitter.util.{Time, Duration, Future}
import com.twitter.zipkin.config.{JsConfig, CssConfig}
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.gen
import com.twitter.zipkin.query.{TraceSummary, QueryRequest}
import java.text.SimpleDateFormat
import java.util.Calendar
import java.lang.Throwable
import com.twitter.zipkin.common.json.ZipkinJson

/**
 * Application that handles ZipkinWeb routes
 * @param client Thrift client to ZipkinQuery
 */
class App(
  rootUrl: String,
  pinTtl: Duration,
  jsConfig: JsConfig,
  cssConfig: CssConfig,
  client: gen.ZipkinQuery.FinagledClient,
  statsReceiver: StatsReceiver) extends Controller(statsReceiver) { self =>

  val log = Logger.get()
  val dateFormat = new SimpleDateFormat("MM-dd-yyyy")
  val timeFormat = new SimpleDateFormat("HH:mm:ss")
  def getDate = dateFormat.format(Calendar.getInstance().getTime)
  def getTime = timeFormat.format(Calendar.getInstance().getTime)

  /* Index page */
  get("/") { request =>
    /* If valid query params passed, run the query and push the data down with the page */
    val queryRequest = QueryExtractor(request)
    val queryResults = queryRequest match {
      case None => {
        /* Not valid params, load the normal landing page */
        Future(Seq.empty[TraceSummary])
      }
      case Some(qr) => {
        /* Valid params */
        query(qr, request)
      }
    }
    getServices.map { services =>
      queryResults.map { results =>
        queryRequest match {
          case None => render.view(wrapView(new IndexView("Index", getDate, getTime, services, results)))
          case Some(qRequest) => {
            render.view(wrapView(
              new IndexView(
                qRequest.serviceName,
                getDate,
                getTime,
                services,
                results,
                qRequest.annotations,
                qRequest.binaryAnnotations.map {
                  _.map { b =>
                    (b.key, new String(b.value.array, b.value.position, b.value.remaining))
                  }
                })))
          }
        }
      }
    }.flatten
  }

  /* Trace page */
  get("/traces/:id") { request =>
    render.view(wrapView(new ShowView(request.routeParams("id")))).toFuture
  }

  /* Static page for render trace from JSON */
  get("/static") { request =>
    render.view(wrapView(new StaticView)).toFuture
  }

  get("/aggregates") {request =>
    render.view(wrapView(new AggregatesView(getDate))).toFuture
  }

  /**
   * API: query
   * Returns query results that satisfy the request parameters in order of descending duration
   *
   * Required GET params:
   * - serviceName: String
   * - endDate: date String formatted to `QueryRequest.fmt`
   *
   * Optional GET params:
   * - limit: Int, default 100
   * - spanName: String
   * - timeAnnotation: String
   * - annotationKey, annotation_value: String
   * - adjust_clock_skew = (true|false), default true
   */
  get("/api/query") { request =>
    render.header("Content-Type", "application/json")
    query(request).map(response => render.body(ZipkinJson.generate(response)))
  }

  def query(request: Request): Future[Seq[TraceSummary]] = {
    QueryExtractor(request) match {
      case Some(qr) => query(qr, request)
      case None     => Future(Seq.empty)
    }
  }

  def query(queryRequest: QueryRequest, request: Request, retryLimit: Int = 10): Future[Seq[TraceSummary]] = {
    log.debug(queryRequest.toString)
    /* Get trace ids */
    val response = client.getTraceIds(queryRequest.toThrift).map { _.toQueryResponse }
    val adjusters = getAdjusters(request)

    response.map { resp =>
      resp.traceIds match {
        case Nil => {
          if ((queryRequest.annotations.map { _.length } getOrElse 0) +
              (queryRequest.binaryAnnotations.map { _.length } getOrElse 0) > 0 && retryLimit > 0) {
            /* Complex query, so retry */
            query(queryRequest.copy(endTs = resp.endTs), request, retryLimit - 1)
          } else {
            Future.value(Seq.empty)
          }
        }
        case ids @ _ => {
          client.getTraceSummariesByIds(ids, adjusters).map {
            _.map { summary =>
              summary.toTraceSummary
            }
          }
        }

      }
    }.flatten
  }

  /**
   * API: services
   * Returns the total list of services Zipkin is aware of
   */
  get("/api/services") { request =>
    log.debug("/api/services")
    getServices.map {
      render.json(_)
    }
  }

  def getServices: Future[Seq[TracedService]] = {
    client.getServiceNames().map { services =>
      services.toSeq.sorted.map { name =>
        TracedService(name)
      }
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
    withServiceName(request) { serviceName =>
      client.getSpanNames(serviceName).map { spans =>
        render.json {
          spans.toSeq.sorted.map { s =>
            Map("name" -> s)
          }
        }
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
   * API: dependencies
   * Returns all services paired with every service they call in to
   *
   * Required GET params:
   * - startTime: Date in epoch seconds (this will be rounded to the nearest day)
   * - endTime: Optional date in epoch seconds (rounded to the nearest day)
   */
  get("/api/dependencies/?:startTime?/?:endTime?") { request =>
    val startTime = request.routeParams.getOrElse("startTime", Time.now.inSeconds.toString).toLong
    val endTime = request.routeParams.get("endTime").map(_.toLong)

    client.getDependencies(startTime, endTime).map { deps =>
      render.header("Content-Type", "application/json")
      render.body(ZipkinJson.generate(deps))
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
   * Get the id param from the http request (which is a hex string) and convert it
   * to it's numerical value.
   */
  def getParamTraceId(request: Request) : Long = {
    val paramStr = request.routeParams("id")

    /* purposely throw an exception if the span is malformed and cannot be converted */
    SpanId.fromString(paramStr).map(_.toLong).get
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
    val ids = Seq(getParamTraceId(request))
    log.debug(ids.toString())

    client.getTraceCombosByIds(ids, adjusters).map { _.map { _.toTraceCombo }.head }.map { combo =>
      render.header("Content-Type", "application/json")
      render.body(ZipkinJson.generate(combo))
    }
  }

  get("/api/trace/:id") { request =>
    log.info("/api/trace")
    val adjusters = getAdjusters(request)
    val ids = Seq(getParamTraceId(request))
    log.debug(ids.toString())

    client.getTraceCombosByIds(ids, adjusters).map {
      _.map {
        _.toTraceCombo.trace
      }.head
    }.map { trace =>
      render.header("Content-Type", "application/json")
      render.body(ZipkinJson.generate(trace))
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
    val id = getParamTraceId(request)
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
    val id = getParamTraceId(request)
    request.routeParams("state").toLowerCase match {
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


  error { request =>
    request.error match {
      case Some(thrown:Throwable) =>
        val errorMsg = Option(thrown.getMessage).getOrElse("Unknown error")
        val stacktrace = Option(thrown.getStackTraceString).getOrElse("")
        render.status(500).view(wrapView(new ErrorView(errorMsg + "\n\n\n" + stacktrace))).toFuture
      case _ =>
        render.status(500).body("Unknown error in finatra").toFuture
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
        Future.value(pinTtl.inSeconds)
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
    request.routeParams.get("adjust_clock_skew") match {
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

  private def wrapView(v: TitledView) = new TitledView  {
    val pageTitle = v.pageTitle
    val template = "templates/layouts/application.mustache"
    val rootUrl = self.rootUrl
    val innerView: View = v
    val javascripts = jsConfig.resources
    val stylesheets = cssConfig.resources
    lazy val body = innerView.render
  }
}

trait TitledView extends View {
  val pageTitle:String
}

class IndexView(
  val pageTitle: String,
  val endDate: String,
  val endTime: String,
  services: Seq[TracedService] = Nil,
  queryResults: Seq[TraceSummary] = Nil,
  annotations: Option[Seq[String]] = None,
  kvAnnotations: Option[Seq[(String, String)]] = None
) extends TitledView {
  val template = "templates/index.mustache"
  val jsonServices = ZipkinJson.generate(services)
  val jsonQueryResults = ZipkinJson.generate(queryResults)

  lazy val annotationsPartial = annotations.map {
    _.map { a =>
      new View {
        val template = "public/templates/query-add-annotation.mustache"
        val visible = true
        val value = a
      }.render
    }.fold("") { _ + _ }
  }

  lazy val kvAnnotationsPartial = kvAnnotations.map {
    _.map { b =>
      new View {
        val template = "public/templates/query-add-kv.mustache"
        val visible = true
        val key = b._1
        val value = b._2
      }.render
    }
  }
}

class ShowView(val traceId: String) extends TitledView {
  val pageTitle = "Trace %s".format(traceId)
  val template = "templates/show.mustache"
}

class StaticView extends TitledView {
  val pageTitle = "Static"
  val template = "templates/static.mustache"
}

class AggregatesView(val endDate: String) extends TitledView {
  val pageTitle = "Aggregates"
  val template = "templates/aggregates.mustache"
}

class ErrorView(val errorMsg: String) extends TitledView {
  val pageTitle = "ERROR"
  val template = "templates/error.mustache"
}
