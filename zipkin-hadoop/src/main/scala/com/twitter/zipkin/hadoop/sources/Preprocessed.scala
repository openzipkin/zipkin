package com.twitter.zipkin.hadoop.sources

import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Annotation}
import com.twitter.scalding._
import com.twitter.zipkin.gen
import scala.collection.JavaConverters._


class Preprocessed(args : Args) extends Job(args) with DefaultDateRangeJob {
  SpanSource()
    .read
    .mapTo(0 ->('trace_id, 'name, 'id, 'parent_id, 'annotations, 'binary_annotations)) {
      s: Span => (s.trace_id, s.name, s.id, s.parent_id, s.annotations.toList, s.binary_annotations.toList)
    }
    .groupBy('trace_id, 'name, 'id, 'parent_id) {
      _.reduce('annotations, 'binary_annotations) {
      (left: (List[Annotation], List[BinaryAnnotation]), right: (List[Annotation], List[BinaryAnnotation])) =>
      (left._1 ++ right._1, left._2 ++ right._2)
    }
  }.mapTo(('trace_id, 'name, 'id, 'parent_id, 'annotations, 'binary_annotations) -> 'span) {
    a : (Long, String, Long, Long, List[Annotation], List[BinaryAnnotation]) =>
      new gen.Span(a._1, a._2, a._3, a._5.asJava, a._6.asJava).setParent_id(a._4)
  }.write(PrepSpanSource())
}
