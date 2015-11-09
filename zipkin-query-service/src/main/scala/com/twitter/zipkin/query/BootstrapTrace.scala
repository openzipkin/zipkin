package com.twitter.zipkin.query

import com.twitter.finagle.tracing.{Annotation, DefaultTracer, Record, Trace}
import com.twitter.util.Time
import com.twitter.zipkin.Constants
import scala.collection.mutable.ArrayBuffer

/** This decouples bootstrap tracing from trace initialization. */
object BootstrapTrace {
  private val id = Trace.nextId
  private val records = ArrayBuffer[Record]()

  record(Annotation.ServiceName("zipkin-query"))
  record(Annotation.BinaryAnnotation(Constants.LocalComponent, "finatra"))
  record(Annotation.Rpc("bootstrap"))
  record("init")

  private def record(ann: Annotation): Unit = records += Record(id, Time.now, ann, None)

  def record(message: String): Unit = record(Annotation.Message(message))

  /** records duration and flushes the trace */
  def complete() = {
    // TODO: DeadlineSpanMap needs an update to mark complete based on Span.duration
    record(Annotation.ClientRecv())
    records.foreach(DefaultTracer.self.record)
  }
}
