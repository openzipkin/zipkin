package com.twitter.zipkin.receiver.kafka

import com.twitter.zipkin.gen.{Span => ThriftSpan}
import com.twitter.ostrich.admin.{Service => OstrichService, ServiceTracker}
import kafka.consumer.{Consumer, ConsumerConnector, ConsumerConfig}
import kafka.serializer.Decoder
import com.twitter.util.{Closable, CloseAwaitably, FuturePool, Future, Time}
import java.util.concurrent.{TimeUnit, Executors}
import java.util.Properties
import java.net.{SocketAddress, InetSocketAddress}

object KafkaProcessor {

  type KafkaDecoder = Decoder[Option[List[ThriftSpan]]]

  def apply(
    topics:Map[String, Int],
    config: Properties,
    process: Seq[ThriftSpan] => Future[Unit],
    decoder: KafkaDecoder
  ): KafkaProcessor = new KafkaProcessor(topics, config, process, decoder)
}

class KafkaProcessor(
  topics: Map[String, Int],
  config: Properties,
  process: Seq[ThriftSpan] => Future[Unit],
  decoder: KafkaProcessor.KafkaDecoder
) extends Closable with CloseAwaitably {
  private[this] val processorPool = {
    val consumerConnector: ConsumerConnector = Consumer.create(new ConsumerConfig(config))
    val threadCount = topics.foldLeft(0) { case (sum, (_, a)) => sum + a }
    val pool = Executors.newFixedThreadPool(threadCount)
    for {
      (topic, streams) <- consumerConnector.createMessageStreams(topics, decoder)
      stream <- streams
    } pool.submit(new KafkaStreamProcessor(stream, process))
    pool
  }

  def close(deadline: Time): Future[Unit] = closeAwaitably {
    FuturePool.unboundedPool {
      processorPool.shutdown()
      processorPool.awaitTermination(deadline.inMilliseconds, TimeUnit.MILLISECONDS)
    }
  }
}
