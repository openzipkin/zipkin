package com.twitter.zipkin.receiver.kafka

import com.twitter.logging.Logger
import kafka.consumer.KafkaStream
import kafka.message.{Message, MessageAndMetadata}
import com.twitter.zipkin.common.Span
import com.twitter.util.Future

case class KafkaStreamProcessor(
  stream: KafkaStream[Option[List[Span]]],
  process: Seq[Span] => Future[Unit]
) extends Runnable {

  val log = Logger.get(getClass.getName)

  def run() {
    log.debug("%s run" format(KafkaStreamProcessor.getClass.getName))
    try {
      stream.foreach(msg => {
        log.debug("processing event %s" format(msg.message))
        msg.message map { process(_) }
      })
    } catch {
      case e: Exception => log.error("exception : %s : %s".format(e, e.getMessage))
    }
  }

}
