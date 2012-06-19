package com.twitter.zipkin.hadoop

/**
 * Created with IntelliJ IDEA.
 * User: jli
 * Date: 6/15/12
 * Time: 10:56 AM
 * To change this template use File | Settings | File Templates.
 */

import com.twitter.scalding._
import com.twitter.zipkin.hadoop.sources
import sources.SpanSource
import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Constants, Annotation}
import collection.mutable.HashMap


class SlowestService (args : Args) extends Job(args) with DefaultDateRangeJob {

  val preprocessed = SpanSource()
    .read
    .mapTo(0 ->('trace_id, 'id, 'parent_id, 'annotations, 'binary_annotations)) {
    s: Span => (s.trace_id, s.id, s.parent_id, s.annotations.toList, s.binary_annotations.toList)
  }
    .groupBy('trace_id, 'id, 'parent_id) {
    _.reduce('annotations, 'binary_annotations) {
      (left: (List[Annotation], List[BinaryAnnotation]), right: (List[Annotation], List[BinaryAnnotation])) =>
        (left._1 ++ right._1, left._2 ++ right._2)
    }
  }
    // let's find those server annotations and convert into service name and duration
  val result = preprocessed.flatMap('annotations -> 'service_durations) {
    annotations: List[Annotation] =>
      var clientSend: Option[Annotation] = None
      var clientReceived: Option[Annotation] = None
      annotations.foreach {
        a =>
          if (Constants.CLIENT_SEND.equals(a.getValue)) clientSend = Some(a)
          if (Constants.CLIENT_RECV.equals(a.getValue)) clientReceived = Some(a)
      }
      for (cs <- clientSend; cr <- clientReceived)
      yield (cs.getHost.service_name -> (cr.timestamp - cs.timestamp) / 1000)
  }
    .project('trace_id, 'service_durations)
    .groupBy('trace_id) {
    _.sortBy('service_durations).reverse.take(10)
    }
    .write(Tsv(args("output")))
}

