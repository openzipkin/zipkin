
package com.twitter.zipkin.hadoop

import com.twitter.scalding.{DefaultDateRangeJob, Job, Args}
import sources.SpanSource
import com.twitter.zipkin.gen.{Annotation, BinaryAnnotation, Span, Constants}

class SpanToTrace(args: Args) extends Job(args) with DefaultDateRangeJob {

  val preprocessed = SpanSource()
    .read
    .mapTo(0 -> ('trace_id, 'id, 'parent_id, 'annotations, 'binary_annotations))
      { s: Span => (s.trace_id, s.id, s.parent_id, s.annotations.toList, s.binary_annotations.toList) }
    .groupBy('trace_id, 'id, 'parent_id) { _.reduce('annotations, 'binary_annotations) {
      mergeAnnotations[Annotation, BinaryAnnotation]
    }
  }.groupBy('trace_id) { _.reduce('annotations, 'binary_annotations) {
      mergeAnnotations[Annotation, BinaryAnnotation]
    }
  }

  def mergeAnnotations[A, B](left: (List[A], List[B]), right: (List[A], List[B])) = {
    (left._1 ++ right._1, left._2 ++ right._2)
  }

}

