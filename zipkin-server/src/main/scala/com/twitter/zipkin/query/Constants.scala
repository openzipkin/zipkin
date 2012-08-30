package com.twitter.zipkin.query

import com.twitter.conversions.time._
import com.twitter.util.Duration

object Constants {
  /* Amount of time padding to use when resolving complex query timestamps */
  val TraceTimestampPadding: Duration = 1.minute
}
