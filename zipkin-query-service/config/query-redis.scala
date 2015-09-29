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

import com.twitter.conversions.time._
import com.twitter.finagle.builder.ClientBuilder
import com.twitter.finagle.redis.util.StringToChannelBuffer
import com.twitter.finagle.redis.{Client, Redis}
import com.twitter.util.Await
import com.twitter.zipkin.builder.QueryServiceBuilder
import com.twitter.zipkin.storage.redis.RedisSpanStore

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

val authPassword = sys.env.get("REDIS_PASSWORD")
if (authPassword.isDefined) {
  Await.result(client.auth(StringToChannelBuffer(authPassword.get)))
}

val spanStore = new RedisSpanStore(client, Some(7.days))

QueryServiceBuilder(
  "0.0.0.0:" + serverPort,
  adminPort,
  logLevel,
  spanStore
)