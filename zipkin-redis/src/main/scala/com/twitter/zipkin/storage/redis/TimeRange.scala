package com.twitter.zipkin.storage.redis

/**
 * Represents the range of time in microseconds from epoch.
 *
 * @param startTs microseconds from epoch for the first annotation in a trace.
 * @param stopTs microseconds from epoch for the first annotation in a trace.
 */
case class TimeRange(startTs: Long, stopTs: Long)
