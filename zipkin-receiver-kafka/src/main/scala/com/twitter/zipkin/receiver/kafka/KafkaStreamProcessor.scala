package com.twitter.zipkin.receiver.kafka

import com.twitter.logging.Logger
import kafka.consumer.KafkaStream
import com.twitter.zipkin.gen.{Span => ThriftSpan}
import com.twitter.util.{Await, Future}
import java.io._

case class KafkaStreamProcessor(
  stream: KafkaStream[Option[List[ThriftSpan]]],
  process: Seq[ThriftSpan] => Future[Unit]
  ) extends Runnable {

  private[this] val log = Logger.get(getClass.getName)

  def run() {
    log.debug("%s run" format(KafkaStreamProcessor.getClass.getName))
    try {
      stream foreach { msg =>
        log.debug("processing event %s" format (msg.message))
        msg.message map { spans => Await.result(process(spans))}
      }
    }
    catch {
      case e: Exception => log.error("exception : %s : %s" format(e, e.getMessage))
    }
  }

}
