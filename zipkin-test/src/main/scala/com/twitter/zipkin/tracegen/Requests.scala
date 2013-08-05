package com.twitter.zipkin.tracegen

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

import com.twitter.zipkin.gen
import java.nio.ByteBuffer
import com.twitter.logging.Logger
import com.twitter.scrooge.BinaryThriftStructSerializer
import org.apache.thrift.protocol.TBinaryProtocol
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.thrift.ThriftClientFramedCodec
import scala.{List, Seq}
import com.twitter.util.Await

class Requests(collectorHost: String, collectorPort: Int, queryHost: String, queryPort: Int) {

  val log = Logger.get(getClass.getName)

  def logTraces(traces: scala.List[gen.Trace]): Unit = {
    val protocol = new TBinaryProtocol.Factory()
    val service = ClientBuilder()
      .hosts(collectorHost + ":" + collectorPort)
      .hostConnectionLimit(1)
      .codec(ThriftClientFramedCodec())
      .build()

    val client = new gen.ZipkinCollector.FinagledClient(service, protocol)

    val serializer = new BinaryThriftStructSerializer[gen.Span] {
      def codec = gen.Span
    }

    traces.foreach(t => {
      t.spans.foreach(s => {
        val entries = List(gen.LogEntry("zipkin", serializer.toString(s)))
        println("Sending: " + s + ". Response: " + Await.result(client.log(entries)))
      })
    })

    service.close()
  }

  def printTrace(traceIds: Seq[Long], client: gen.ZipkinQuery.FinagledClient) {
    val traces = Await.result(client.getTracesByIds(traceIds, List(gen.Adjust.TimeSkew)))
    traces.foreach {
      trace =>
        trace.spans.foreach {
          s =>
            println("Got span: " + s)
        }
    }
  }

  def querySpan(service: String, span: String, annotation: String,
                kvAnnotation: (String, ByteBuffer), maxTraces: Int): Unit = {
    val protocol = new TBinaryProtocol.Factory()
    val serviceClient = ClientBuilder()
      .hosts(queryHost + ":" + queryPort)
      .hostConnectionLimit(1)
      .codec(ThriftClientFramedCodec())
      .build()
    val client = new gen.ZipkinQuery.FinagledClient(serviceClient, protocol)

    println("Querying for service name: " + service + " and span name " + span)
    val ts1 = Await.result(client.getTraceIdsBySpanName(service, span, Long.MaxValue, maxTraces, gen.Order.DurationDesc))
    printTrace(ts1, client)

    println("Querying for service name: " + service)
    val ts2 = Await.result(client.getTraceIdsBySpanName(service, "", Long.MaxValue, maxTraces, gen.Order.DurationDesc))
    printTrace(ts2, client)

    println("Querying for annotation: " + annotation)
    val ts3 = Await.result(client.getTraceIdsByAnnotation(service, annotation, ByteBuffer.wrap("".getBytes), Long.MaxValue, maxTraces, gen.Order.DurationDesc))
    printTrace(ts3, client)

    println("Querying for kv annotation: " + kvAnnotation)
    val ts4 = Await.result(client.getTraceIdsByAnnotation(service, kvAnnotation._1, kvAnnotation._2, Long.MaxValue, maxTraces, gen.Order.DurationDesc))
    printTrace(ts4, client)

    val traces = Await.result(client.getTracesByIds(ts4, List(gen.Adjust.TimeSkew)))
    println(traces.toString)

    val traceTimeline = Await.result(client.getTraceTimelinesByIds(ts4, List(gen.Adjust.TimeSkew)))

    println("Timeline:")
    println(traceTimeline.toString)

    println("Data ttl: " + Await.result(client.getDataTimeToLive()))
    println("Service names: " + Await.result(client.getServiceNames()))
    println("Span names for : " + service + " " + Await.result(client.getSpanNames(service)))

    serviceClient.close()
  }


}