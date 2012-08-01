//package com.twitter.zipkin.hadoop
//
//import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Annotation}
//import com.twitter.scalding.{Tsv, DefaultDateRangeJob, Job, Args}
//import sources.{SpanSource1, PrepNoNamesSpanSource1, PrepNoNamesSpanSource, SpanSource}
//
//class FindMissingSpansIncoming(args: Args) extends Job(args) with DefaultDateRangeJob {
//
//  val preprocessed = SpanSource1()
//    .read
//    //    .filter(0)                                // with this commented out  span_id_5
//    //      { s: Span => s.isSetParent_id() }
//    .mapTo(0 -> ('trace_id, 'id, 'parent_id, 'annotations, 'binary_annotations))
//  { s: Span => (s.trace_id, s.id, s.parent_id, s.annotations.toList, s.binary_annotations.toList) }
//    .groupAll( _.size('count) )
//    //    .groupBy('trace_id, 'id, 'parent_id) { _.reduce('annotations, 'binary_annotations) {
//    //      (left: (List[Annotation], List[BinaryAnnotation]), right: (List[Annotation], List[BinaryAnnotation])) =>
//    //      (left._1 ++ right._1, left._2 ++ right._2)
//    //    }
//    //  }
//    //
//    //  val ids = preprocessed
//    //    .project('id, 'annotations)
//    //    .rename('id -> 'parent_id_1)
//    //    .rename('annotations -> 'a)
//    //
//    //  val find_parents = preprocessed
//    //    .filter('parent_id) { x : Long => x != 0 }
//    //    .project('id, 'parent_id)
//    //    .leftJoinWithSmaller('parent_id -> 'parent_id_1, ids)
//    //    .map('a -> 'has_parent) { a : List[Annotation] => if (a == null) "no_parent" else "parent" }
//    //    .groupBy('has_parent) { _.size('count) }
//    .write(Tsv(args("output")))
//}
