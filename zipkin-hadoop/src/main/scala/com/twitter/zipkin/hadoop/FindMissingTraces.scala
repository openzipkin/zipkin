package com.twitter.zipkin.hadoop

import com.twitter.zipkin.gen
import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Annotation}
import com.twitter.scalding.DefaultDateRangeJob
import com.twitter.scalding.{Tsv, DefaultDateRangeJob, Job, Args}
import com.twitter.zipkin.hadoop.sources.SpanSource
import scala.collection.JavaConverters._
import collection.immutable.HashSet

class FindMissingTraces(args : Args) extends Job(args) with DefaultDateRangeJob {
    val preprocessed =
      SpanSource()
      .read
//      .filter(0)
//    { s: Span => s.isSetParent_id() }
      .mapTo(0 -> ('trace_id, 'name, 'id, 'parent_id, 'annotations, 'binary_annotations))
        { s: Span => (s.trace_id, s.name, s.id, s.parent_id, s.annotations.toList, s.binary_annotations.toList) }
      .groupBy('trace_id, 'name, 'id, 'parent_id) { _.reduce('annotations, 'binary_annotations) {
      (left: (List[Annotation], List[BinaryAnnotation]), right: (List[Annotation], List[BinaryAnnotation])) =>
        (left._1 ++ right._1, left._2 ++ right._2)
      }
    }


    val trace = preprocessed
      .map(('trace_id, 'name, 'id,'parent_id, 'annotations, 'binary_annotations) -> 'spanList) { t : (Long, String, Long, Long, List[Annotation], List[BinaryAnnotation]) =>
        val (tid, name, id, pid, a, ba) = t
        List(new gen.Span(tid, name, id, a.asJava, ba.asJava).setParent_id(pid))
      }
      .groupBy('trace_id) {
        _.reduce('spanList) {
          (left : List[Span], right : List[Span]) => left ++ right
        }
      }
      .filter('spanList) {
        l : List[Span] => {
          var ids = new HashSet[Long]
          l.foreach { s : Span => if (s != null) ids += s.id }
          l.exists {s : Span => s!= null && (s.parent_id != 0) && !ids.contains(s.parent_id) }
        }
      }.flatMapTo('spanList -> 'span) { sl : List[Span] => sl }
      .write(Tsv(args("output")))
}
