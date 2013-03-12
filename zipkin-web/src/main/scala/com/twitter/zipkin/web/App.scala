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

import com.codahale.jerkson.Json
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finatra.{Response, Controller, View, Request}
import com.twitter.logging.Logger
import com.twitter.util.{Duration, Future}
import com.twitter.zipkin.config.{JsConfig, CssConfig}
import com.twitter.zipkin.conversions.json._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.common.json.JsonTraceSummary
import com.twitter.zipkin.gen
import com.twitter.zipkin.query.QueryRequest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.lang.Throwable

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

  /* FIXME - delete this when we upgrade to Finatra > 1.3.0.  We can't upgrade now because
   * cassie can't support finagle 6 and Finatra depends on it. */
  def getWithErrorHandler(uri: String)(callback: Request => Future[Response]) {
    get(uri){ request =>
      callback(request).handle { case e:Exception =>
        request.error = Some(e)
        val handler = this.errorHandler.get // this is ensured to succeed because of error{} below
        handler(request).get()
      }
    }
  }

  /* Index page */
  getWithErrorHandler("/") { request =>
    /* If valid query params passed, run the query and push the data down with the page */
    val queryRequest = QueryExtractor(request)
    val queryResults = queryRequest match {
      case None => {
        /* Not valid params, load the normal landing page */
        Future(Seq.empty[JsonTraceSummary])
      }
      case Some(qr) => {
        /* Valid params */
        query(qr, request)
      }
    }
    getServices.map { services =>
      queryResults.map { results =>
        queryRequest match {
          case None => render.view(wrapView(new IndexView(getDate, getTime, services, results)))
          case Some(qRequest) => {
            render.view(wrapView(
              new IndexView(
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
  getWithErrorHandler("/traces/:id") { request =>
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
  getWithErrorHandler("/api/query") { request =>
    query(request).map(render.json(_))
  }

  def query(request: Request): Future[Seq[JsonTraceSummary]] = {
    QueryExtractor(request) match {
      case Some(qr) => query(qr, request)
      case None     => Future(Seq.empty)
    }
  }

  def query(queryRequest: QueryRequest, request: Request, retryLimit: Int = 10): Future[Seq[JsonTraceSummary]] = {
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
              summary.toTraceSummary.toJson
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
  getWithErrorHandler("/api/services") { request =>
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
  getWithErrorHandler("/api/spans") { request =>
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
  getWithErrorHandler("/api/top_annotations") { request =>
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
  getWithErrorHandler("/api/top_kv_annotations") { request =>
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
  getWithErrorHandler("/api/get/:id") { request =>
    log.info("/api/get")
    val adjusters = getAdjusters(request)
    val ids = Seq(request.routeParams("id").toLong)
    log.debug(ids.toString())

    client.getTraceCombosByIds(ids, adjusters).map { _.map { _.toTraceCombo }.head }.map { combo =>
      render.json(combo.toJson)
    }
  }

  getWithErrorHandler("/api/trace/:id") { request =>
    log.info("/api/trace")
    val adjusters = getAdjusters(request)
    val ids = Seq(request.routeParams("id").toLong)
    log.debug(ids.toString())

    client.getTraceCombosByIds(ids, adjusters).map {
      _.map {
        _.toTraceCombo.trace
      }.head
    }.map { trace =>
      render.json(trace.toJson)
    }
  }

  /**
   * API: is_pinned
   * Returns whether a trace has been pinned
   *
   * Required GET params:
   * - id: Long
   */
  getWithErrorHandler("/api/is_pinned/:id") { request =>
    val id = request.routeParams("id").toLong
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
    val id = request.routeParams("id").toLong
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

  private def wrapView(v: View) = new View {
    val template = "templates/layouts/application.mustache"
    val rootUrl = self.rootUrl
    val innerView: View = v
    val javascripts = jsConfig.resources
    val stylesheets = cssConfig.resources
    lazy val body = innerView.render
  }
}

class IndexView(
  val endDate: String,
  val endTime: String,
  services: Seq[TracedService] = Nil,
  queryResults: Seq[JsonTraceSummary] = Nil,
  annotations: Option[Seq[String]] = None,
  kvAnnotations: Option[Seq[(String, String)]] = None
) extends View {
  val template = "templates/index.mustache"
  val jsonServices = Json.generate(services)
  val jsonQueryResults = Json.generate(queryResults)

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

class ShowView(val traceId: String) extends View {
  val template = "templates/show.mustache"
}

class StaticView extends View {
  val template = "templates/static.mustache"
}

class AggregatesView(val endDate: String) extends View {
  val template = "templates/aggregates.mustache"
}

class ErrorView(val errorMsg: String) extends View {
  val template = "templates/error.mustache"
}
