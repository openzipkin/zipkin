/*
 * Copyright 2012 Twitter Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.twitter.logging.{ConsoleHandler, Level, LoggerFactory}
import com.twitter.zipkin.anormdb.{SpanStoreBuilder, DependencyStoreBuilder}
import com.twitter.zipkin.receiver.kafka.KafkaSpanReceiverFactory
import com.twitter.zipkin.storage.anormdb.DB
import com.twitter.zipkin.collector.builder.{ZipkinServerBuilder, Adjustable, CollectorServiceBuilder}
import com.twitter.zipkin.storage.Store

val serverPort = sys.env.get("COLLECTOR_PORT").getOrElse("9410").toInt
val adminPort = sys.env.get("COLLECTOR_ADMIN_PORT").getOrElse("9900").toInt
val logLevel = sys.env.get("COLLECTOR_LOG_LEVEL").getOrElse("INFO")
val sampleRate = sys.env.get("COLLECTOR_SAMPLE_RATE").getOrElse("1.0").toDouble

val db = DB()

val storeBuilder = Store.Builder(SpanStoreBuilder(db, true), DependencyStoreBuilder(db))
val kafkaReceiver = sys.env.get("KAFKA_ZOOKEEPER").map(
  KafkaSpanReceiverFactory.factory(_, sys.env.get("KAFKA_TOPIC").getOrElse("zipkin"))
)

val loggerFactory = new LoggerFactory(
  node = "",
  level = Level.parse(logLevel),
  handlers = List(ConsoleHandler())
)

CollectorServiceBuilder(
  storeBuilder,
  kafkaReceiver,
  serverBuilder = ZipkinServerBuilder(serverPort, adminPort).loggers(List(loggerFactory))
).sampleRate(Adjustable.local(sampleRate))
