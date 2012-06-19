package com.twitter.zipkin.hadoop.sources

import com.twitter.scalding._
import com.twitter.zipkin.hadoop.sources
import sources.SpanSource
import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Constants, Annotation}

/**
 * Created with IntelliJ IDEA.
 * User: jli
 * Date: 6/15/12
 * Time: 12:59 PM
 * To change this template use File | Settings | File Templates.
 */

class Preprocess(args : Args) extends Job(args) with DefaultDateRangeJob {

  SpanSource()
    .read
    .mapTo(0 -> ('trace_id, 'id, 'parent_id, 'annotations, 'binary_annotations))
    { s: Span => (s.trace_id, s.id, s.parent_id, s.annotations.toList, s.binary_annotations.toList) }
      .groupBy('trace_id, 'id, 'parent_id) { _.reduce('annotations, 'binary_annotations) {
        (left: (List[Annotation], List[BinaryAnnotation]), right: (List[Annotation], List[BinaryAnnotation])) =>
        (left._1 ++ right._1, left._2 ++ right._2)
      }
    }.write(Tsv(args("output")))

}
