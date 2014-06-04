package com.twitter.zipkin.receiver.kafka

import com.twitter.logging.Logger
import com.twitter.ostrich.admin.{Service => OstrichService, ServiceTracker}
import com.twitter.util.{Closable, CloseAwaitably, Future, FuturePool, Time}
import com.twitter.zipkin.common._
import java.net.{SocketAddress, InetSocketAddress}
import java.util.Properties
import java.util.concurrent.{TimeUnit, Executors}
import kafka.consumer.{Consumer, ConsumerConnector, ConsumerConfig}
import kafka.serializer.Decoder


object KafkaProcessor {
  type KafkaDecoder = Decoder[Option[List[Span]]]

  def apply(
    topics:Map[String, Int],
    config: Properties,
    process: Seq[Span] => Future[Unit],
    decoder: KafkaDecoder
  ): KafkaProcessor = new KafkaProcessor(topics, config, process, decoder)
}

class KafkaProcessor(
  topics: Map[String, Int],
  config: Properties,
  process: Seq[Span] => Future[Unit],
  decoder: KafkaProcessor.KafkaDecoder
) extends Closable with CloseAwaitably {
  // find out the number of topics and associated number of requested threads
  private[this] val processorPool = {
    val consumerConnector: ConsumerConnector = Consumer.create(new ConsumerConfig(config))
    val threadCount = topics.foldLeft(0) { case (sum, (_, i)) => sum + i }
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
