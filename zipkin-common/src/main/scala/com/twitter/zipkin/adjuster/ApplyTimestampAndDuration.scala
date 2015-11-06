package com.twitter.zipkin.adjuster

import com.twitter.zipkin.common._

/**
 * This applies timestamp and duration to spans, based on interpretation of
 * annotations. Spans who already have timestamp and duration set and left
 * alone.
 *
 * After application, spans without a timestamp are filtered out, as they are
 * not possible to present on a timeline. The only scenario where this is
 * possible is when instrumentation sends binary annotations ahead of the span
 * start event, or when a span's start even was lost. Considering this is error
 * -case or transient, there's no option to control this behavior.
 */
object ApplyTimestampAndDuration extends ((List[Span]) => List[Span]) {

  override def apply(spans: List[Span]): List[Span] = spans.map { span =>
    if (span.timestamp.isDefined && span.duration.isDefined) span else apply(span)
  }.filter(_.timestamp.nonEmpty).sorted

  def apply(span: Span) = {
    val sorted = span.annotations.sorted
    val firstOption = sorted.headOption.map(_.timestamp)
    val lastOption = sorted.lastOption.map(_.timestamp)
    span.copy(
      timestamp = span.timestamp.orElse(firstOption),
      duration = span.duration.orElse {
        for (first <- firstOption; last <- lastOption; if (first != last))
          yield last - first
      }
    )
  }
}
