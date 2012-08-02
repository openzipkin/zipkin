package com.twitter.zipkin.hadoop

import com.twitter.zipkin.gen.{Annotation, Span}
import com.twitter.scalding.{Tsv, DefaultDateRangeJob, Job, Args}
import sources.SpanSource

class GrepByAnnotation(args: Args) extends Job(args) with DefaultDateRangeJob {

  val grepByWord = args.required("word")

  val preprocessed =
    SpanSource()
      .read
      .mapTo(0 -> ('traceid, 'annotations)) { s: Span => (s.trace_id, s.annotations.toList) }
      .filter('annotations) { annotations: List[Annotation] =>
        !annotations.filter(p => p.value.toLowerCase().contains(grepByWord)).isEmpty
      }
      .project('traceid)
      .write(Tsv(args("output")))
}