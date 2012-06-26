package com.twitter.zipkin.hadoop

import com.twitter.scalding._
import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Constants, Annotation}
import cascading.pipe.joiner.LeftJoin
import sources.{Util, SpanSource}

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

  val spanInfo = preprocessed
    .project('id, 'parent_id, 'annotations)
    .flatMap('annotations -> ('cService, 'service)){ Util.getClientAndServiceName }
    .project('id, 'parent_id, 'cService, 'service)

  val idName = spanInfo
    .project('id, 'service)
    .filter('service) {n : String => n != null }
    .unique('id, 'service)
    .rename('id, 'id1)
    .rename('service, 'parentService)

  val result = spanInfo
    .joinWithSmaller('parent_id -> 'id1, idName, joiner = new LeftJoin) // dep_test_3
    .map(('cService, 'parentService) -> ('cService, 'parentService)){ n : (String, String) =>
      if (n._2 == null) {
        (n._1, n._1)
      } else n
    }
   .groupBy('service, 'parentService){ _.size('count) }
   .groupBy('service){ _.sortBy('count) }
    .write(Tsv(args("output")))
}
