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
package com.twitter.zipkin.tracegen

import collection.mutable.ListBuffer
import com.twitter.conversions.time._
import com.twitter.util.Time
import com.twitter.zipkin.common._
import com.twitter.zipkin.conversions.thrift._
import com.twitter.zipkin.thriftscala
import java.nio.ByteBuffer
import scala.util.Random

private object TraceGen {
  val rnd = new Random

  val serviceNames =
    """vitae ipsum felis lorem magna dolor porta donec augue tortor auctor
       mattis ligula mollis aenean montes semper magnis rutrum turpis sociis
       lectus mauris congue libero rhoncus dapibus natoque gravida viverra egestas
       lacinia feugiat pulvinar accumsan sagittis ultrices praesent vehicula nascetur
       pharetra maecenas consequat ultricies ridiculus malesuada curabitur convallis
       facilisis hendrerit penatibus imperdiet tincidunt parturient adipiscing
       consectetur pellentesque
    """.split("""\s+""")

  def rndSvcName: String = serviceNames(rnd.nextInt(serviceNames.size))

  val rpcNames =
    """vivamus fermentum semper porta nunc diam velit adipiscing ut tristique vitae
    """.split("""\s+""")

  def rndRpcName: String = rpcNames(rnd.nextInt(rpcNames.size))
}

class TraceGen(traces: Int, maxDepth: Int) {
  import TraceGen._

  def apply(): Seq[Span] =
    (0 until traces) flatMap { _ =>
      val start = (rnd.nextInt(8) + 1).hours.ago
      val trace = new GenTrace
      withEndpoint(doRpc(trace, start, rnd.nextInt(maxDepth), rndRpcName, _))
      trace.spans.toSeq
    }

  private class GenTrace {
    val traceId = rnd.nextLong
    val spans = new ListBuffer[Span]()
    def addSpan(name: String, id: Long, parentId: Option[Long], annos: List[Annotation], binAnnos: List[BinaryAnnotation]) {
      spans += Span(traceId, name, id, parentId, annos, binAnnos)
    }
  }

  private[this] var upstreamServices = Set.empty[String]
  private[this] def withEndpoint[T](f: Endpoint => T): T = {
    // attempt to get a service name without introducing a loop in the trace DAG
    var svcName = rndSvcName
    var attempts = serviceNames.size
    while (attempts > 0 && upstreamServices.contains(svcName)) {
      svcName = rndSvcName
      attempts -= 1
    }

    // couldn't find one. create a new one with a random suffix
    if (attempts == 0)
      svcName += (rnd.nextInt(8000) + 1000)

    upstreamServices += svcName
    val ret = f(Endpoint(rnd.nextInt(), (rnd.nextInt(8000) + 1000).toShort, svcName))
    upstreamServices -= svcName

    ret
  }

  private[this] def doRpc(
    trace: GenTrace,
    time: Time,
    depth: Int,
    spanName: String,
    ep: Endpoint,
    spanId: Long = rnd.nextLong,
    parentSpanId: Option[Long] = None
  ): Time = {
    var curTime = time + 1.millisecond

    val svrAnnos = new ListBuffer[Annotation]()
    svrAnnos += Annotation(curTime.inMicroseconds, thriftscala.Constants.SERVER_RECV, Some(ep))

    val svrBinAnnos = (0 to rnd.nextInt(3)) map { _ =>
      BinaryAnnotation(rndSvcName, ByteBuffer.wrap(rndSvcName.getBytes), AnnotationType.String, Some(ep))
    } toList

    // simulate some amount of work
    curTime += rnd.nextInt(10).milliseconds

    val times = (0 to (rnd.nextInt(5) + 1)) map { _ =>
      svrAnnos += Annotation(curTime.inMicroseconds, rndSvcName, Some(ep))
      curTime += rnd.nextInt(5).milliseconds
    }

    if (depth > 0) {
      // parallel calls to downstream services
      val times = (0 to (rnd.nextInt(depth) + 1)) map { _ =>
        withEndpoint { nextEp =>
          val thisSpanId = rnd.nextLong
          val thisParentId = Some(spanId)
          val rpcName = rndRpcName
          val annos = new ListBuffer[Annotation]()
          val binAnnos = new ListBuffer[BinaryAnnotation]()

          val delay = (if (rnd.nextInt(10) > 6) rnd.nextInt(10) else 0).microseconds
          annos += Annotation((curTime + delay).inMicroseconds, thriftscala.Constants.CLIENT_SEND, Some(nextEp))
          val time = doRpc(trace, curTime, rnd.nextInt(depth), rpcName, nextEp, thisSpanId, thisParentId) + 1.millisecond
          annos += Annotation(time.inMicroseconds, thriftscala.Constants.CLIENT_RECV, Some(nextEp))

          trace.addSpan(rpcName, thisSpanId, thisParentId, annos.toList, binAnnos.toList)

          time
        }
      }
      curTime = times.max
    }

    svrAnnos += Annotation(curTime.inMicroseconds, thriftscala.Constants.SERVER_SEND, Some(ep))
    trace.addSpan(spanName, spanId, parentSpanId, svrAnnos.toList, svrBinAnnos.toList)
    curTime
  }
}
