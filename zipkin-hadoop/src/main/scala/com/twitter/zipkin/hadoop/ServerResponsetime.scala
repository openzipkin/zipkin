/*
 * Copyright 2012 Twitter Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.zipkin.hadoop

import com.twitter.scalding._
import com.twitter.zipkin.hadoop.sources.{TimeGranularity, SpanSource}
import com.twitter.zipkin.gen.{Span, Constants, Annotation}
import scala.collection.JavaConverters._
import java.nio.ByteBuffer
import java.net.{Inet4Address, Inet6Address, InetAddress}

/**
 * Let's calculate how long it takes for a server to respond. The idea being
 * that we can find servers that are unusually slow compared to others running the same service.
 */
class ServerResponsetime(args: Args) extends Job(args) with UtcDateRangeJob {

  val serverAnnotations = Seq(Constants.SERVER_RECV, Constants.SERVER_SEND)

  SpanSource(TimeGranularity.Hour)
    .read
    // only need id and annotations for this
    .mapTo(0 -> ('id, 'annotations)) { s: Span => (s.id, s.annotations.toList) }
    .groupBy('id) { data =>
      // merge annotations from all span objects into one list
      data.reduce('annotations) {
        (annotations: List[Annotation], serverAnnotations: List[Annotation]) =>
          //we only care about server annotations
          val filtered = annotations.filter((a) => serverAnnotations.contains(a.getValue))
          filtered ++ serverAnnotations
      }
    }
    // let's find those server annotations and convert into service name and duration
    .flatMap('annotations -> ('ipv4, 'service, 'duration)) { annotations: List[Annotation] =>
      var serverSend: Option[Annotation] = None
      var serverReceived: Option[Annotation] = None
      annotations.foreach { a =>
        if (Constants.SERVER_RECV.equals(a.getValue)) serverReceived = Some(a)
        if (Constants.SERVER_SEND.equals(a.getValue)) serverSend = Some(a)
      }
      // only return a value if we have both annotations
      for (ss <- serverSend; sr <- serverReceived)
        yield (sr.getHost.getIpv4, ss.getHost.service_name, (ss.timestamp - sr.timestamp) / 1000)
    }
    // we want to see how a service is doing on a particular host
    .groupBy('ipv4, 'service) { _.sizeAveStdev('duration -> ('count, 'average, 'stddev)) }
    // throw out the machine/service combos with very few entries
    .filter('count) { count: Int => count > 100 } // TODO kinda hacky
    .map('ipv4 -> 'ipv4) { ipv4: Int => getAddress(ipv4).getHostName }
    .write(Tsv(args("output")))


  def getAddress(ipv4: Int): InetAddress = {
    val array = new Array[Byte](4)
    val bb = ByteBuffer.wrap(array)
    bb.putInt(ipv4)
    InetAddress.getByAddress(array)
  }
}
