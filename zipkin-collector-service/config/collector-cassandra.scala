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
import com.twitter.app.App
import com.twitter.zipkin.builder.Scribe
import com.twitter.zipkin.cassandra
import com.twitter.zipkin.cassandra.CassandraSpanStoreFactory
import com.twitter.zipkin.collector.builder.CollectorServiceBuilder
import com.twitter.zipkin.storage.Store
import org.twitter.zipkin.storage.cassandra.ZipkinRetryPolicy
import scala.collection.mutable.ListBuffer

val contactPoints: Array[String] = sys.env.get("CASSANDRA_CONTACT_POINTS").getOrElse("localhost")
  .split(",")

val cassandraUser = sys.env.get("CASSANDRA_USER")
val cassandraPass = sys.env.get("CASSANDRA_PASS")

var args = new ListBuffer[String]()
if (cassandraUser.isDefined && cassandraPass.isDefined) {
  args += "-zipkin.store.cassandra.user"
  args += cassandraUser.get
  args += "-zipkin.store.cassandra.password"
  args += cassandraPass.get
}

object CollectorService extends App with CassandraSpanStoreFactory
CollectorService.nonExitingMain(args.toArray)
val cluster = CollectorService.createClusterBuilder().build()

val storeBuilder = Store.Builder(new cassandra.SpanStoreBuilder(cluster))

CollectorServiceBuilder(Scribe.Interface(categories = Set("zipkin")))
  .writeTo(storeBuilder)
