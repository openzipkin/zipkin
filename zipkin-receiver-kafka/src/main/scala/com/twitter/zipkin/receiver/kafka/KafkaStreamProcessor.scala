package com.twitter.zipkin.receiver.kafka

import com.twitter.logging.Logger
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import kafka.consumer.KafkaStream

case class KafkaStreamProcessor[T](
  stream: KafkaStream[T, Option[List[ThriftSpan]]],
  process: Seq[ThriftSpan] => Future[Unit]
  ) extends Runnable {

  private[this] val log = Logger.get(getClass.getName)

  def run() {
    log.debug(s"${KafkaStreamProcessor.getClass.getName} run")
    try {
      stream foreach { msg =>
        log.debug(s"processing event ${msg.message()}")
        msg.message map { spans => Await.result(process(spans))}
      }
    }
    catch {
      case e: Exception =>
        e.printStackTrace()
        log.error(s"${e.getCause}")
    }
  }

}
