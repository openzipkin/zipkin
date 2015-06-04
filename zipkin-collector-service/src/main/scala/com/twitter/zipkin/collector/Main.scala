/*
 * Copyright 2012 Twitter Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.twitter.zipkin.collector



import com.twitter.finagle.stats.StatsReceiver
import com.twitter.server.TwitterServer
import com.twitter.util.{Await, Future}
import com.twitter.zipkin.cassandra.CassieSpanStoreFactory
import com.twitter.zipkin.collector.{SpanReceiver, ZipkinQueuedCollectorFactory}
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}

import com.twitter.zipkin.receiver.kafka.{KafkaProcessor, KafkaDecoderImpl, KafkaSpanReceiverFactory}
import com.twitter.zipkin.storage.WriteSpanStore
import com.twitter.zipkin.zookeeper.ZooKeeperClientFactory

object ZipkinKafkaCollectorServer extends TwitterServer
with ZipkinQueuedCollectorFactory
with CassieSpanStoreFactory
with ZooKeeperClientFactory
with KafkaSpanReceiverFactory
{
  def newReceiver(receive: Seq[ThriftSpan] => Future[Unit], stats: StatsReceiver): SpanReceiver =
  newKafkaSpanReceiver(receive, stats.scope("kafkaSpanReceiver"),Some(KafkaProcessor.defaultKeyDecoder), new KafkaDecoderImpl)

  def newSpanStore(stats: StatsReceiver): WriteSpanStore =
    newCassandraStore(stats.scope("cassie"))

  def main() {
    val collector = newCollector(statsReceiver)
    onExit { collector.close() }
    Await.ready(collector)
  }
}
