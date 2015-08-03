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
package com.twitter.zipkin.cassandra

import com.datastax.driver.core.Cluster;
import com.twitter.app.App
import com.twitter.conversions.time._
import com.twitter.finagle.{Addr, Name, Resolver}
import com.twitter.finagle.stats.{DefaultStatsReceiver, StatsReceiver}
import com.twitter.zipkin.thriftscala.{Span => ThriftSpan}
import com.twitter.zipkin.storage.cassandra._

trait CassandraSpanStoreFactory { self: App =>
  import com.twitter.zipkin.storage.cassandra.{CassandraSpanStoreDefaults => Defaults}

  val cassieColumnFamilies = Defaults.ColumnFamilyNames
  val cassieSpanCodec = Defaults.SpanCodec

  val keyspace = flag("zipkin.store.cassandra.keyspace", Defaults.KeyspaceName, "name of the keyspace to use")
  val cassieDest = flag("zipkin.store.cassandra.dest", "localhost:9160", "dest of the cassandra cluster")

  val cassieSpanTtl = flag("zipkin.store.cassie.spanTTL", Defaults.SpanTtl, "length of time cassandra should store spans")
  val cassieIndexTtl = flag("zipkin.store.cassie.indexTTL", Defaults.IndexTtl, "length of time cassandra should store span indexes")
  val cassieIndexBuckets = flag("zipkin.store.cassie.indexBuckets", Defaults.IndexBuckets, "number of buckets to split index data into")

  val cassieMaxTraceCols = flag("zipkin.store.cassie.maxTraceCols", Defaults.MaxTraceCols, "max number of spans to return from a query")
  val cassieReadBatchSize = flag("zipkin.store.cassie.readBatchSize", Defaults.ReadBatchSize, "max number of rows per query")

  def newCassandraStore(
    cluster: Cluster,
    stats: StatsReceiver = DefaultStatsReceiver.scope("cassie")
  ): CassandraSpanStore = {

    val repository = new org.twitter.zipkin.storage.cassandra.Repository(keyspace(), cluster)

    // @fixme ... add all the options

    new CassandraSpanStore(
      repository,
      stats.scope(keyspace()),
      cassieColumnFamilies,
      cassieSpanTtl(),
      cassieIndexTtl(),
      cassieIndexBuckets(),
      cassieMaxTraceCols(),
      cassieReadBatchSize(),
      cassieSpanCodec)
  }
}
