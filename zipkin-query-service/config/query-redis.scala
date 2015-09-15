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

import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.redis.{Client, Redis}
import com.twitter.logging.{ConsoleHandler, Level, LoggerFactory}
import com.twitter.zipkin.builder.{ZipkinServerBuilder, QueryServiceBuilder}
import com.twitter.zipkin.redis
import com.twitter.zipkin.storage.Store

val serverPort = sys.env.get("QUERY_PORT").getOrElse("9411").toInt
val adminPort = sys.env.get("QUERY_ADMIN_PORT").getOrElse("9901").toInt
val logLevel = sys.env.get("QUERY_LOG_LEVEL").getOrElse("INFO")

val host = sys.env.get("REDIS_HOST").getOrElse("0.0.0.0")
val port = sys.env.get("REDIS_PORT").map(_.toInt).getOrElse(6379)

val client = Client(ClientBuilder().hosts(host + ":" + port)
                                   .hostConnectionLimit(4)
                                   .hostConnectionCoresize(4)
                                   .codec(Redis())
                                   .build())

val storeBuilder = Store.Builder(redis.SpanStoreBuilder(client, authPassword = sys.env.get("REDIS_PASSWORD")))

val loggerFactory = new LoggerFactory(
  node = "",
  level = Level.parse(logLevel),
  handlers = List(ConsoleHandler())
)

QueryServiceBuilder(
  storeBuilder,
  serverBuilder = ZipkinServerBuilder(serverPort, adminPort).loggers(List(loggerFactory))
)