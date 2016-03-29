package com.twitter.zipkin.receiver.kafka

import com.twitter.logging.Logger
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.common.Span
import kafka.consumer.KafkaStream

case class KafkaStreamProcessor[T](
  stream: KafkaStream[T, List[Span]],
  process: Seq[Span] => Future[Unit]
  ) extends Runnable {

  private[this] val log = Logger.get(getClass.getName)

  def run() {
    log.debug(s"${KafkaStreamProcessor.getClass.getName} run")
    stream.foreach { msg =>
      var spans: List[Span] = null
      try {
        spans = msg.message()
        Await.result(process(spans))
      } catch {
        case e: Exception =>
          if (spans == null) {
            log.debug(s"malformed message: ${e.getMessage}")
          } else {
            // The exception could be related to a span being huge. Instead of filling logs,
            // print trace id, span id pairs
            val traceToSpanId = spans.map(s => s.traceId + " -> " + s.id).mkString(",")
            log.error(e, s"unhandled error processing traceId -> spanId: ${traceToSpanId}")
          }
      }
    }
  }
}
