package com.twitter.zipkin.common

/** Utilities for working on traces */
object Trace {

  /** What's the total duration of the spans in this trace? */
  def duration(spans: List[Span]): Option[Long] = {
    // turns (timestamp, timestamp + duration) into an ordered list
    val timestamps: List[Long] = spans.flatMap(s => s.timestamp.map(ts =>
      s.duration.map(d => List(ts, (ts + d))).getOrElse(List(ts))
    ).getOrElse(List.empty)).sorted
    // first and last timestamp are boundaries of the span, and their difference is the duration.
    timestamps.lastOption.flatMap { last =>
      timestamps.headOption.flatMap(first => if (last == first) None else Some(last - first))
    }
  }
}
