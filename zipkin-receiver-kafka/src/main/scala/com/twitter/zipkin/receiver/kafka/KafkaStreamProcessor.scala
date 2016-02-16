package com.twitter.zipkin.receiver.kafka

import com.twitter.logging.Logger
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import kafka.consumer.KafkaStream
import org.apache.thrift.protocol.TProtocolException

case class KafkaStreamProcessor[T](
  stream: KafkaStream[T, List[ThriftSpan]],
  process: Seq[ThriftSpan] => Future[Unit]
  ) extends Runnable {

  private[this] val log = Logger.get(getClass.getName)

  def run() {
    log.debug(s"${KafkaStreamProcessor.getClass.getName} run")
    try {
      stream.foreach { msg =>
        try {
          Await.result(process(msg.message()))
        } catch {
          case e: TProtocolException =>
            log.debug(s"malformed message: ${e.getMessage}")
        }
      }
    }
    catch {
      case e: Exception =>
        log.error(e, s"${e.getCause}")
    }
  }

}
