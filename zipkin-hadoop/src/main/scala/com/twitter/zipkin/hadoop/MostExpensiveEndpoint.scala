package com.twitter.zipkin.hadoop

import com.twitter.scalding._
import com.twitter.zipkin.hadoop.sources
import sources.SpanSource
import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Constants, Annotation}

class MostExpensiveEndpoint(args : Args) extends Job(args) with DefaultDateRangeJob {

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
    .project('annotations)
    .flatMapTo('annotations -> 'temp){ al : List[Annotation] =>
    var clientSent: Option[Annotation] = None
    var serverReceived : Option[Annotation] = None
    var serverSent : Option[Annotation] = None
    var clientReceived : Option[Annotation] = None

    al.foreach { a : Annotation =>
      if (Constants.CLIENT_SEND.equals(a.getValue)) clientSent = Some(a)
      if (Constants.SERVER_RECV.equals(a.getValue)) serverReceived = Some(a)
      if (Constants.SERVER_SEND.equals(a.getValue)) serverSent = Some(a)
      if (Constants.CLIENT_RECV.equals(a.getValue)) clientReceived = Some(a)
    }
    for (cs <- clientSent; sr <- serverReceived; ss <- serverSent; cr <- clientReceived)
      yield List((cs.getHost.service_name, sr.getHost.service_name, (cr.timestamp - cs.timestamp) / 1000),
            (sr.getHost.service_name, cs.getHost.service_name, (ss.timestamp - sr.timestamp) / 1000))
  }.flatMap('temp -> ('service1, 'service2, 'duration)){a : List[(String, String, Int)] => a}
    .groupBy('service1, 'service2){ _.average('duration) }
   .groupBy('service1){ _.sortBy('duration).reverse.take(10) }
  .write(Tsv(args("output")))

}

