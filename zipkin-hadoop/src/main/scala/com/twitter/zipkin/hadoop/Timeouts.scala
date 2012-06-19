package com.twitter.zipkin.hadoop

import com.twitter.scalding._
import com.twitter.zipkin.hadoop.sources
import sources.SpanSource
import scala.collection.JavaConverters._
import java.nio.ByteBuffer
import java.net.{Inet4Address, Inet6Address, InetAddress}
import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Constants, Annotation}

class Timeouts(args: Args) extends Job(args) with DefaultDateRangeJob {

  val ERROR_TYPE = List("finagle.timeout", "finagle.retry")

  val serverAnnotations = Seq(Constants.SERVER_RECV, Constants.SERVER_SEND)

  val preprocessed = SpanSource()
    .read
    .mapTo(0 -> ('trace_id, 'id, 'parent_id, 'annotations, 'binary_annotations))
     { s: Span => (s.trace_id, s.id, s.parent_id, s.annotations.toList, s.binary_annotations.toList) }
      .groupBy('trace_id, 'id, 'parent_id) { _.reduce('annotations, 'binary_annotations) {
        (left: (List[Annotation], List[BinaryAnnotation]), right: (List[Annotation], List[BinaryAnnotation])) =>
        (left._1 ++ right._1, left._2 ++ right._2)
      }
    }

  val result = preprocessed
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
    }.project('cService, 'sService)
    .groupBy('cService, 'sService){ _.size('numTimeouts) }
    .write(Tsv(args("output")))
}
