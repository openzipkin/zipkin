package io.zipkin.sbt

import sbt._

/**
 * swallow all the messages
 */
object NullLogger extends BasicLogger {
  def trace(t: => Throwable) {}
  def log(level: Level.Value, message: => String) {}
  def logAll(events: Seq[LogEvent]) {}
  def success(message: => String) {}
  def control(event: ControlEvent.Value, message: => String) {}
}
