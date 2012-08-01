package com.twitter.zipkin.hadoop

import com.twitter.zipkin.gen
import com.twitter.zipkin.gen.{BinaryAnnotation, Span, Annotation}
import com.twitter.scalding.DefaultDateRangeJob
import com.twitter.scalding.{Tsv, DefaultDateRangeJob, Job, Args}
import com.twitter.zipkin.hadoop.sources.SpanSource
import scala.collection.JavaConverters._
import collection.immutable.HashSet

class GetSingleTrace(args : Args) extends Job(args) with DefaultDateRangeJob {
  val preprocessed =
    SpanSource()
    .read
    .mapTo(0 -> ('trace_id, 'name, 'id, 'parent_id, 'annotations, 'binary_annotations))
      { s: Span => (s.trace_id, s.name, s.id, s.parent_id, s.annotations.toList, s.binary_annotations.toList) }
    .filter('trace_id){ tid : Long => tid == 1487130643109130000L }
    .write(Tsv(args("output")))
}