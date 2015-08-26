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

import com.twitter.app.App
import com.twitter.zipkin.builder.{ZipkinServerBuilder, Scribe}
import com.twitter.zipkin.cassandra
import com.twitter.zipkin.cassandra.CassandraSpanStoreFactory
import com.twitter.zipkin.collector.builder.{Adjustable, CollectorServiceBuilder}
import com.twitter.zipkin.storage.Store
import com.twitter.logging.LoggerFactory
import com.twitter.logging.Level
import com.twitter.logging.ConsoleHandler

object Factory extends App with CassandraSpanStoreFactory

Factory.cassandraDest.parse(sys.env.get("CASSANDRA_CONTACT_POINTS").getOrElse("localhost"))

val username = sys.env.get("CASSANDRA_USERNAME")
val password = sys.env.get("CASSANDRA_PASSWORD")
val sampleRate = sys.env.get("COLLECTOR_SAMPLE_RATE").getOrElse("1.0").toDouble
val queueNumWorkers = sys.env.get("COLLECTOR_QUEUE_NUM_WORKERS").getOrElse("10").toInt
val queueMaxSize = sys.env.get("COLLECTOR_QUEUE_MAX_SIZE").getOrElse("500").toInt
val serverPort = sys.env.get("COLLECTOR_PORT").getOrElse("9410").toInt
val adminPort = sys.env.get("COLLECTOR_ADMIN_PORT").getOrElse("9900").toInt
val logLevel = sys.env.get("COLLECTOR_LOG_LEVEL").getOrElse("DEBUG")


if (username.isDefined && password.isDefined) {
  Factory.cassandraUsername.parse(username.get)
  Factory.cassandraPassword.parse(password.get)
}

val cluster = Factory.createClusterBuilder().build()
val storeBuilder = Store.Builder(new cassandra.SpanStoreBuilder(cluster))

val loggerFactory = new LoggerFactory(
  node = "",
  level = Level.parse(logLevel),
  handlers = List(ConsoleHandler())
)

CollectorServiceBuilder(
  interface = Scribe.Interface(categories = Set("zipkin")),
  serverBuilder = ZipkinServerBuilder(serverPort, adminPort).loggers(List(loggerFactory))
).writeTo(storeBuilder)
  .sampleRate(Adjustable.local(sampleRate))
  .queueNumWorkers(queueNumWorkers)
  .queueMaxSize(queueMaxSize)
