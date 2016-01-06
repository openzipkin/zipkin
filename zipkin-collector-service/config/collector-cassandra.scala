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

import com.google.common.util.concurrent.Atomics
import com.twitter.app.App
import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.cassandra.CassandraSpanStoreFactory
import com.twitter.zipkin.collector.builder.{CollectorServiceBuilder, ZipkinServerBuilder}
import com.twitter.zipkin.receiver.kafka.KafkaSpanReceiverFactory
import com.twitter.zipkin.storage.{DependencyStore, SpanStore, Store}

val serverPort = sys.env.get("COLLECTOR_PORT").getOrElse("9410").toInt
val adminPort = sys.env.get("COLLECTOR_ADMIN_PORT").getOrElse("9900").toInt
val logLevel = sys.env.get("COLLECTOR_LOG_LEVEL").getOrElse("INFO")
val sampleRate = sys.env.get("COLLECTOR_SAMPLE_RATE").getOrElse("1.0").toFloat

object Factory extends App with CassandraSpanStoreFactory

Factory.ensureSchema.parse(sys.env.get("CASSANDRA_ENSURE_SCHEMA").getOrElse("true"))
Factory.cassandraDest.parse(sys.env.get("CASSANDRA_CONTACT_POINTS").getOrElse("localhost"))

val username = sys.env.get("CASSANDRA_USERNAME")
val password = sys.env.get("CASSANDRA_PASSWORD")

if (username.isDefined && password.isDefined) {
  Factory.cassandraUsername.parse(username.get)
  Factory.cassandraPassword.parse(password.get)
}

val storeBuilder = Store.Builder(
  new Builder[SpanStore]() {
    override def apply() = Factory.newCassandraStore()
  },
  new Builder[DependencyStore]() {
    override def apply() = Factory.newCassandraDependencies()
  }
)

val kafkaReceiver = sys.env.get("KAFKA_ZOOKEEPER").map(
  KafkaSpanReceiverFactory.factory(_,
    sys.env.get("KAFKA_TOPIC").getOrElse("zipkin"),
    sys.env.get("KAFKA_STREAMS").getOrElse("1").toInt
  )
)

CollectorServiceBuilder(
  storeBuilder,
  kafkaReceiver,
  serverBuilder = ZipkinServerBuilder(serverPort, adminPort),
  sampleRate = Atomics.newReference(sampleRate),
  logLevel = logLevel
)
