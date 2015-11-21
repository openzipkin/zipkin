package com.twitter.zipkin.adjuster

import com.twitter.zipkin.common._

/**
 * This applies timestamp and duration to spans, based on interpretation of
 * annotations. Spans who already have timestamp and duration set and left
 * alone.
 *
 * <p/>After application, spans without a timestamp are filtered out, as they are
 * not possible to present on a timeline. This is possible on incomplete spans,
 * or when a span's start even was lost. Considering this is error-case or
 * transient, there's no option to control this behavior.
 */
object ApplyTimestampAndDuration extends ((List[Span]) => List[Span]) {

  override def apply(spans: List[Span]): List[Span] =
    spans.map(apply).filter(_.timestamp.nonEmpty).sorted

  /** Looks at annotations to fill missing [[Span.timestamp]] and [[Span.duration]] */
  def apply(span: Span): Span = {
    // Don't overwrite authoritatively set timestamp and duration!
    if (span.timestamp.isDefined && span.duration.isDefined) {
      return span
    }
    // Only calculate span.timestamp and duration on complete spans. This avoids
    // persisting an inaccurate timestamp due to a late arriving annotation.
    if (span.annotations.size < 2) {
      return span
    }
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
