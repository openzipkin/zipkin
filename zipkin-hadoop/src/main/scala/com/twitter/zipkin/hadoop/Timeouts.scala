package com.twitter.zipkin.hadoop

import com.twitter.scalding._
import com.twitter.zipkin.hadoop.sources
import sources.SpanSource
import com.twitter.zipkin.gen.{Span, Constants, Annotation}
import scala.collection.JavaConverters._
import java.nio.ByteBuffer
import java.net.{Inet4Address, Inet6Address, InetAddress}

/**
 * Created with IntelliJ IDEA.
 * User: jli
 * Date: 6/13/12
 * Time: 5:03 PM
 * To change this template use File | Settings | File Templates.
 */

class Timeouts(args: Args) extends Job(args) with DefaultDateRangeJob {

  val ERROR_TYPE = List("finagle.timeout", "finagle.retry")

  val serverAnnotations = Seq(Constants.SERVER_RECV, Constants.SERVER_SEND)

  SpanSource()
    .read
    .mapTo(0 -> ('id, 'parent_id, 'annotations)) { s: Span => (s.id, s.parent_id, s.annotations.toList) }
    .groupBy('id, 'parent_id) { data =>
    // merge annotations from all span objects into one list
      data.reduce('annotations) {
        (annotations: List[Annotation], annotations1: List[Annotation]) =>
          annotations ++ annotations1
      }
    }
    .filter('annotations){annotations : List[Annotation] => annotations.exists({a : Annotation =>  a.value == "finagle.timeout"})}
    .flatMap('annotations -> ('cService, 'sService)) { annotations: List[Annotation] =>
      var clientSend: Option[Annotation] = None
      var serverReceived: Option[Annotation] = None
      annotations.foreach { a =>
        if (Constants.CLIENT_SEND.equals(a.getValue)) clientSend = Some(a)
        if (Constants.SERVER_RECV.equals(a.getValue)) serverReceived = Some(a)
      }
      // only return a value if we have both annotations
      for (cs <- clientSend; sr <- serverReceived)
      yield (cs.getHost.service_name, sr.getHost.service_name)
    }.groupBy('cService, 'sService){ _.size('numTimeouts) }
    .write(Tsv(args("output")))
}
