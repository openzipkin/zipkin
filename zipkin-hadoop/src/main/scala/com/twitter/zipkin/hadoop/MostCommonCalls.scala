package com.twitter.zipkin.hadoop

import com.twitter.scalding._
import sources.SpanSource
import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Constants, Annotation}

class MostCommonCalls(args : Args) extends Job(args) with DefaultDateRangeJob {
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
    .flatMap('annotations -> ('called, 'callee)){ al : List[Annotation] =>
    var clientSent: Option[Annotation] = None
    var serverReceived : Option[Annotation] = None

    al.foreach { a : Annotation =>
      if (Constants.CLIENT_SEND.equals(a.getValue)) clientSent = Some(a)
      if (Constants.SERVER_RECV.equals(a.getValue)) serverReceived = Some(a)
    }
    for (cs <- clientSent; sr <- serverReceived)
    yield (sr.getHost.service_name,cs.getHost.service_name)
  }.groupBy('called, 'callee){ _.size('count) }
   .groupBy('called){ _.sortBy('count) }
    .write(Tsv(args("output")))
}
