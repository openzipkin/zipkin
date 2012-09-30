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
import collection.mutable.ListBuffer
import scala.util.Random
import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift._

/**
 * Here be dragons. Not terribly nice dragons.
 */
class TraceGen {

  val random = new Random()
  var serviceNameNo = 0
  var methodNameNo = 0

  def generate(genTraces: Int, maxSpanDepth: Int): List[gen.Trace] = {
    val traces = ListBuffer[gen.Trace]()
    for (i <- 0 until genTraces) {
      val traceId = random.nextLong
      traces.append(generateTrace(traceId, maxSpanDepth))
    }
    traces.toList
  }

  def generateTrace(traceId: Long, maxSpanDepth: Int): gen.Trace = {

    val spanDepth = random.nextInt(maxSpanDepth) + 1
    val startTimestamp = (System.currentTimeMillis * 1000) - (24 * 60 * 60 * 1000 * 1000L)
    val endTimestamp = startTimestamp + 4 * 1000

    gen.Trace(generateSpans(spanDepth, 1, traceId, None, startTimestamp, endTimestamp))
  }

  def generateSpans(depthRemaining: Int, width: Int, traceId: Long,
                    parentSpanId: Option[Long], startTimestamp: Long, endTimestamp: Long): List[gen.Span] = {

    if (depthRemaining <= 0) return List()

    val timeStep = ((endTimestamp - startTimestamp) / width).toLong
    if (timeStep <= 0) return List()

    var timestamp = startTimestamp

    var rv = List[gen.Span]()
    for (j <- 0 until width) {
      val clientServerSpans = generateSpan(traceId, parentSpanId, timestamp, timestamp + timeStep)
      val genSpans = generateSpans(depthRemaining - 1, math.max(1, random.nextInt(5)), traceId,
        Some(clientServerSpans._1.id), clientServerSpans._3, clientServerSpans._4)

      rv = rv ::: List(clientServerSpans._1, clientServerSpans._2)
      rv = rv ::: genSpans

      timestamp += timeStep
    }
    rv
  }

  def generateSpan(traceId: Long, parentSpanId: Option[Long],
                   startTimestamp: Long, endTimestamp: Long): (gen.Span, gen.Span, Long, Long) = {

    val customAnnotationCount = 5
    val totalAnnotations = customAnnotationCount + 4 // 4 for the reqiured client/server annotations

    val spanId = random.nextLong
    val serviceName = "servicenameexample_" + serviceNameNo
    serviceNameNo += 1
    val spanName = "methodcallfairlylongname_" + methodNameNo
    methodNameNo += 1

    val host1 = Endpoint(random.nextInt(), 1234, serviceName)
    val host2 = Endpoint(random.nextInt(), 5678, serviceName)
    val maxGapMs = math.max(1, ((endTimestamp - startTimestamp) / totalAnnotations).toInt) // ms.

    var timestamp = startTimestamp
    timestamp += random.nextInt(maxGapMs)
    val rvStartTimestamp = timestamp

    val cs = new Annotation(timestamp, gen.Constants.CLIENT_SEND, Some(host1))
    timestamp += random.nextInt(maxGapMs)
    val sr = new Annotation(timestamp, gen.Constants.SERVER_RECV, Some(host2))

    val customAnnotations = List()
    1 to customAnnotationCount foreach (i => {
      timestamp += random.nextInt(maxGapMs)
      customAnnotations :+ new Annotation(timestamp, "some custom annotation", Some(host2))
    })

    timestamp += random.nextInt(maxGapMs)
    val ss = new Annotation(timestamp, gen.Constants.SERVER_SEND, Some(host2))
    timestamp += random.nextInt(maxGapMs)
    val cr = new Annotation(timestamp, gen.Constants.CLIENT_RECV, Some(host1))

    val clientAnnotations = List(cs, cr)
    val spanClient = Span(traceId, spanName, spanId, parentSpanId, clientAnnotations,
      Seq(gen.BinaryAnnotation("key", ByteBuffer.wrap("value".getBytes), gen.AnnotationType.String, None).toBinaryAnnotation))

    val serverAnnotations = List(sr, ss) ::: customAnnotations
    val spanServer = Span(traceId, spanName, spanId, parentSpanId, serverAnnotations, Nil)

    (spanClient.toThrift, spanServer.toThrift, rvStartTimestamp, timestamp)
  }

}
