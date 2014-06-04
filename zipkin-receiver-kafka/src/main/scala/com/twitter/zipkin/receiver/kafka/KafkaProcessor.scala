package com.twitter.zipkin.receiver.kafka

import com.twitter.logging.Logger
import com.twitter.ostrich.admin.{Service => OstrichService, ServiceTracker}
import com.twitter.util.{Closable, CloseAwaitably, Future, Promise, Time}
import com.twitter.zipkin.common._
import java.net.{SocketAddress, InetSocketAddress}
import java.util.Properties
import java.util.concurrent.{TimeUnit, Executors}
import kafka.consumer.{Consumer, ConsumerConnector, ConsumerConfig}
import kafka.serializer.Decoder

trait KafkaDecoder extends Decoder[Option[List[Span]]]

object KafkaProcessor {
  def apply(topics:Map[String, Int],
            config: Properties,
            process: Seq[Span] => Future[Unit],
            decoder: Decoder[Option[List[Span]]]) = {
    val processor = new KafkaProcessor(topics, config, process, decoder)
    processor.init()
    processor
  }
}

class KafkaProcessor(
  topics: Map[String, Int],
  config: Properties,
  process: Seq[Span] => Future[Unit],
  decoder: Decoder[Option[List[Span]]]
) extends Closable
  with CloseAwaitably {
    val consumerConnector: ConsumerConnector = Consumer.create(new ConsumerConfig(config))

    val topicMessageStreams = consumerConnector.createMessageStreams(topics, decoder)

    // find out the number of topics and associated number of requested threads
    val threadCount = (topics foldLeft 0)((sum, a) => sum + a._2)
    val pool = Executors.newFixedThreadPool(threadCount)

    def init(): Unit = {
      for ((topic, streams) <- topicMessageStreams) {
        streams.foreach (s => pool.submit(new KafkaStreamProcessor(s, process)))
      }
    }

    def close(deadline: Time): Future[Unit] = {
      pool.shutdown()
      closeAwaitably {
        pool.awaitTermination(deadline.inMilliseconds, TimeUnit.MILLISECONDS)
        Future.Done
      }
    }
}
