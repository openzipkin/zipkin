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
import java.nio.ByteBuffer

import com.google.common.base.Charsets.UTF_8
import com.twitter.app.App
import com.twitter.finagle.Thrift
import com.twitter.scrooge.BinaryThriftStructSerializer
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.common.Span
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.thriftscala
import com.twitter.zipkin.thriftscala.{Trace, QueryRequest}

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

  private[this] val serializer = new BinaryThriftStructSerializer[thriftscala.Span] { def codec = thriftscala.Span }

  def main() {
    val scribe = Thrift.newIface[thriftscala.Scribe.FutureIface](scribeDest())
    val store = { spans: Seq[Span] =>
      scribe.log(spans.map { span => thriftscala.LogEntry("zipkin", serializer.toString(span.toThrift)) }).unit
    }
    Await.result(generateTraces(store))

    if (!generateOnly()) {
      val client = Thrift.newIface[thriftscala.ZipkinQuery.FutureIface](queryDest())
      Await.result {
        querySpan(
          client,
          "vitae",
          "velit",
          "some custom annotation",
          ("key", ByteBuffer.wrap("value".getBytes)),
          10)
      }
    }
  }

  private[this] def printTrace(traces: Seq[Trace])= {
    for (trace <- traces; span <- trace.spans) yield
      println("Got span: " + span)
  }

  private[this] def querySpan(
    client: thriftscala.ZipkinQuery[Future],
    service: String,
    span: String,
    annotation: String,
    kvAnnotation: (String, ByteBuffer),
    maxTraces: Int
  ): Future[Unit] = {
    println("Querying for service name: " + service + " and span name " + span)
    for {
      ts1 <- client.getTraces(QueryRequest(service, Some(span), None, None, Long.MaxValue, maxTraces, None))
      _ = printTrace(ts1)

      _ = println("Querying for service name: " + service)
      ts2 <- client.getTraces(QueryRequest(service, None, None, None, Long.MaxValue, maxTraces, None))
      _ = printTrace(ts2)

      _ = println("Querying for annotation: " + annotation)
      ts3 <- client.getTraces(QueryRequest(service, None, Some(Seq(annotation)), None, Long.MaxValue, maxTraces, None))
      _ = printTrace(ts3)

      binaryAnnotation = Map(kvAnnotation._1 -> new String(kvAnnotation._2.array(), UTF_8))
      _ = println("Querying for kv annotation: " + kvAnnotation._1)
      ts4 <- client.getTraces(QueryRequest(service, None, None, None, Long.MaxValue, maxTraces, Some(binaryAnnotation)))
      _ = printTrace(ts4)

      traces <- client.getTracesByIds(ts4.map(t => t.spans.head.traceId), List())
      _ = printTrace(traces)

      svcNames <- client.getServiceNames()
      _ = println("Service names: " + svcNames)

      spanNames <- client.getSpanNames(service)
      _ = println("Span names for : " + service + " " + spanNames)
    } yield ()
  }
}
