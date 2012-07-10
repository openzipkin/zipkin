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
import com.twitter.logging.Logger
import com.twitter.zipkin.gen
import java.text.SimpleDateFormat
import java.util.Calendar
import com.twitter.util.Future
import java.nio.ByteBuffer
import com.capotej.finatra_core.FinatraRequest
import com.twitter.zipkin.adapter.{ThriftQueryAdapter, ThriftAdapter}
import com.twitter.zipkin.query.{TimelineAnnotation, TraceCombo, TraceTimeline}
import com.twitter.zipkin.common._

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

    toJson{
      traceIds.map { ids =>
        ids match {
          case Nil => {
            Future.value(Seq.empty)
          }
          case _ => {
            client.getTraceSummariesByIds(ids, adjusters).map {
              _.map { summary =>
                JsonTraceSummary(ThriftAdapter(summary))
              }
            }
          }
        }
      }.flatten.apply()
    }
  }

  get("/api/services") { request =>
    log.debug("/api/services")
    toJson{
      client.getServiceNames().map {
        _.toSeq.sorted
      }.apply()
    }
  }

  get("/api/spans/:serviceName") { request =>
    log.debug("/api/spans/")
    toJson {
      client.getSpanNames(request.params("serviceName")).map {
        _.toSeq.sorted
      }.apply()
    }
  }

  get("/api/top_annotations/:serviceName") { request =>
    toJson {
      client.getTopAnnotations(request.params("serviceName")).map {
        _.toSeq.sorted
      }.apply()
    }
  }

  get("/api/top_kv_annotations/:serviceName") { request =>
    toJson {
      client.getTopKeyValueAnnotations(request.params("serviceName")).map {
        _.toSeq.sorted
      }.apply()
    }
  }

  get("/api/get/:id") { request =>
    log.info("/api/get")
    val adjusters = getAdjusters(request)
    val ids = Seq(request.params("id").toLong)
    log.debug(ids.toString())

    try {
      toJson {
        client.getTraceCombosByIds(ids, adjusters).map { _.map { ThriftQueryAdapter(_) }.head }.map{
            JsonTraceCombo(_)
        }.apply()
      }
    } catch {
      case e: Exception => {
        e.getStackTrace.map { elem =>
          log.info(elem.toString)
        }
        toJson { Seq()}
      }
    }
  }

  get("/is_pinned") { request =>

  }

  post("/pin") { request =>

  }

  def getAdjusters(request: FinatraRequest) = {
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

class QueryObject extends ExportObject {

}

object Globals {
  var rootUrl = "http://localhost/"
  val dateFormat = new SimpleDateFormat("MM-dd-yyyy")
  val timeFormat = new SimpleDateFormat("HH:mm:ss")

  def getDate = dateFormat.format(Calendar.getInstance().getTime)
  def getTime = timeFormat.format(Calendar.getInstance().getTime)
}


object JsonTraceSummary {
  def apply(t: TraceSummary): JsonTraceSummary =
    JsonTraceSummary(t.traceId.toString, t.startTimestamp, t.endTimestamp, t.durationMicro, t.serviceCounts.toMap, t.endpoints)
}
case class JsonTraceSummary(traceId: String, startTimestamp: Long, endTimestamp: Long, durationMicro: Int,
                            serviceCounts: Map[String, Int], endpoints: List[Endpoint])

case class JsonTrace(traceId: String, startTimestamp: Long, endTimestamp: Long, duration: Long, serviceCounts: Map[String, Int])

object JsonTraceCombo {
  def apply(combo: TraceCombo): JsonTraceCombo = {
    val t = combo.trace
    val jsonTrace = t.getStartAndEndTimestamp match {
      case None =>
        JsonTrace(t.id.map(_.toString).getOrElse(""), 0, 0, 0, t.serviceCounts)
      case Some(Timespan(start, end)) =>{
        JsonTrace(t.id.map(_.toString).getOrElse(""), start, end, end - start, t.serviceCounts)
      }
    }
    JsonTraceCombo(jsonTrace, combo.traceSummary.map(JsonTraceSummary(_)), null, combo.spanDepths) //combo.traceSummary, combo.traceTimeline, combo.spanDepths)
  }
}
case class JsonTraceCombo(trace: JsonTrace, traceSummary: Option[JsonTraceSummary], traceTimeline: Option[TraceTimeline],
                      spanDepths: Option[Map[Long, Int]])
