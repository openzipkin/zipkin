package com.twitter.zipkin.hadoop.sources

import com.twitter.zipkin.gen.{BinaryAnnotation, Span, SpanServiceName, Annotation}
import com.twitter.scalding._
import com.twitter.zipkin.gen
import scala.collection.JavaConverters._


class Preprocessed(args : Args) extends Job(args) with DefaultDateRangeJob {
  val preprocessed = SpanSource()
    .read
    .mapTo(0 ->('trace_id, 'name, 'id, 'parent_id, 'annotations, 'binary_annotations)) {
      s: Span => (s.trace_id, s.name, s.id, s.parent_id, s.annotations.toList, s.binary_annotations.toList)
    }
    .groupBy('trace_id, 'name, 'id, 'parent_id) {
      _.reduce('annotations, 'binary_annotations) {
        (left: (List[Annotation], List[BinaryAnnotation]), right: (List[Annotation], List[BinaryAnnotation])) =>
        (left._1 ++ right._1, left._2 ++ right._2)
      }
    }
    .flatMap('annotations -> ('cService, 'service)) { Util.getClientAndServiceName }
    .mapTo(('trace_id, 'name, 'id, 'parent_id, 'annotations, 'binary_annotations, 'cService, 'service) -> 'spanWithServiceNames) {
      a : (Long, String, Long, Long, List[Annotation], List[BinaryAnnotation], String, String) =>
        a match {
          case (tid, name, id, pid, annotations, binary_annotations, cService, service) =>
          {
            val s = new gen.SpanServiceName(tid, name, id, annotations.asJava, binary_annotations.asJava, cService, service)
            s.setParent_id(pid)
          }
        }
    }.write(PreprocessedSpanSource())
}
