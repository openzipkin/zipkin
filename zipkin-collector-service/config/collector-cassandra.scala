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

import com.datastax.driver.core.Cluster
import com.datastax.driver.core.SocketOptions
import com.twitter.zipkin.builder.Scribe
import com.twitter.zipkin.cassandra
import com.twitter.zipkin.collector.builder.CollectorServiceBuilder
import com.twitter.zipkin.storage.Store
import org.twitter.zipkin.storage.cassandra.ZipkinRetryPolicy

val cluster = Cluster.builder()
  .addContactPoint("localhost")
  .withSocketOptions(new SocketOptions().setConnectTimeoutMillis(10000).setReadTimeoutMillis(20000))
  .withRetryPolicy(ZipkinRetryPolicy.INSTANCE)
  .build()

val storeBuilder = Store.Builder(new cassandra.SpanStoreBuilder(cluster))

CollectorServiceBuilder(Scribe.Interface(categories = Set("zipkin")))
  .writeTo(storeBuilder)
