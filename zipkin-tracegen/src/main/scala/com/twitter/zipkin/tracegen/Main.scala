package com.twitter.zipkin.tracegen

/*
 * Copyright 2014 Twitter Inc.
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

import ch.qos.logback.classic.{Level, Logger}
import com.twitter.app.App
import com.twitter.finagle.httpx.Request
import com.twitter.finagle.tracing.SpanId
import com.twitter.finagle.{Httpx, Thrift, param}
import com.twitter.finatra.httpclient.HttpClient
import com.twitter.finatra.json.FinatraObjectMapper
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.common.{Span, Trace}
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.json.{JsonSpan, ZipkinJson}
import com.twitter.zipkin.thriftscala
import org.slf4j.LoggerFactory

trait ZipkinSpanGenerator { self: App =>
  val genTraces = flag("genTraces", 5, "Number of traces to generate")
  val maxDepth = flag("maxDepth", 7, "Max depth of generated traces")

  def generateTraces(store: Seq[Span] => Future[Unit]): Future[Unit] = {
    val traceGen = new TraceGen(genTraces(), maxDepth())
    store(traceGen())
  }
}

object Main extends App with ZipkinSpanGenerator {
  val scribeDest = flag("scribeDest", "localhost:9410", "Destination of the collector")
  val queryDest = flag("queryDest", "localhost:9411", "Destination of the query service")
  val generateOnly = flag("generateOnly", false, "Only generate date, do not request it back")

  /**
   * Initialize a json-aware Finatra client, targeting the query host. Lazy to ensure
   * we get the host after the [[queryDest]] flag has been parsed.
   */
  lazy val queryClient = new HttpClient(
    httpService =
      Httpx.client.configured(param.Label("zipkin-query")).newClient(queryDest()).toService,
    mapper = new FinatraObjectMapper(ZipkinJson)
  )

  LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
    .asInstanceOf[Logger].setLevel(Level.DEBUG)

  private[this] val serializer = new BinaryThriftStructSerializer[thriftscala.Span] { def codec = thriftscala.Span }

  def main() {
    val scribe = Thrift.newIface[thriftscala.Scribe.FutureIface](scribeDest())
    val store = { spans: Seq[Span] =>
      scribe.log(spans.map { span => thriftscala.LogEntry("zipkin", serializer.toString(span.toThrift)) }).unit
    }
    Await.result(generateTraces(store))

    if (!generateOnly()) {
      Await.result {
        querySpan(
          "vitae",
          "velit",
          "some custom annotation".replace(" ", "%20"),
          "key", "value",
          10)
      }
    }
  }

  private[this] def printTrace(traces: Seq[Trace])= {
    for (trace <- traces; span <- trace.spans) yield
      println("Got span: " + span)
  }

  private[this] def querySpan(
    service: String,
    span: String,
    annotation: String,
    key: String,
    value: String,
    limit: Int
  ): Future[Unit] = {
    println(s"Querying for service name: $service and span name $span")
    for {
      ts1 <- getTraces(s"/api/v1/traces?serviceName=$service&spanName=$span&limit=$limit")
      _ = printTrace(ts1)

      _ = println(s"Querying for service name: $service")
      ts2 <- getTraces(s"/api/v1/traces?serviceName=$service&limit=$limit")
      _ = printTrace(ts2)

      _ = println(s"Querying for annotation: $annotation")
      ts3 <- getTraces(s"/api/v1/traces?serviceName=$service&annotationQuery=$annotation&limit=$limit")
      _ = printTrace(ts3)

      _ = println(s"Querying for kv annotation: $key -> $value")
      ts4 <- getTraces(s"/api/v1/traces?serviceName=$service&annotationQuery=$key=$value&limit=$limit")
      _ = printTrace(ts4)

      traceId = ts2.map(t => t.spans.head.traceId).head // map first id to hex
      traces <- queryClient.executeJson[Seq[JsonSpan]](Request("/api/v1/trace/" + SpanId.toString(traceId)))
        .map(_.map(JsonSpan.invert))
        .map(Trace(_))
        .map(Seq(_))
      _ = printTrace(traces)

      svcNames <- queryClient.executeJson[Seq[String]](Request("/api/v1/services"))
      _ = println(s"Service names: $svcNames")

      spanNames <- queryClient.executeJson[Seq[String]](Request(s"/api/v1/spans?serviceName=$service"))
      _ = println(s"Span names for $service: $spanNames")
    } yield ()
  }

  def getTraces(uri: String) = queryClient.executeJson[Seq[Seq[JsonSpan]]](Request(uri))
    .map(traces => traces.map(_.map(JsonSpan.invert)))
    .map(traces => traces.map(Trace(_)))
}
