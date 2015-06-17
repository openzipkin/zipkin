package com.twitter.zipkin.receiver.kafka

import com.twitter.logging.Logger
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import kafka.consumer.KafkaStream

case class KafkaStreamProcessor[T](
  stream: KafkaStream[T, Option[List[ThriftSpan]]],
  process: Seq[ThriftSpan] => Future[Unit]
  ) extends Runnable {


  private val streamIterator  = stream.iterator

  private[this] val log = Logger.get(getClass.getName)

  def run() {
    log.debug(s"${KafkaStreamProcessor.getClass.getName} run")
    while(streamIterator.hasNext){
      streamIterator.next.message map { spans => Await.result(process(spans)) }
    }
  }
}
