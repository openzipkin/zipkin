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

import com.twitter.cassie.codecs.{Utf8Codec, LongCodec, Codec}
import com.twitter.cassie.{ReadConsistency, WriteConsistency, KeyspaceBuilder}
import com.twitter.conversions.time._
import com.twitter.util.{Config, Duration}
import com.twitter.zipkin.gen
import com.twitter.zipkin.storage.cassandra.{CassandraStorage, ScroogeThriftCodec, SnappyCodec}
import com.twitter.zipkin.storage.Storage

case class StorageBuilder(
  keyspaceBuilder: KeyspaceBuilder,
  columnFamily: String = "Traces",
  writeConsistency: WriteConsistency = WriteConsistency.One,
  readConsistency: ReadConsistency = ReadConsistency.One,
  dataTimeToLive: Duration = 7.days,
  readBatchSize: Int = 500,
  spanCodec: Codec[gen.Span] = new SnappyCodec(new ScroogeThriftCodec[gen.Span](gen.Span))
) extends Config[Storage] {

  def columnFamily(c: String):                StorageBuilder = copy(columnFamily = c)
  def writeConsistency(wc: WriteConsistency): StorageBuilder = copy(writeConsistency = wc)
  def readConsistency(rc: ReadConsistency):   StorageBuilder = copy(readConsistency = rc)
  def dataTimeToLive(ttl: Duration):          StorageBuilder = copy(dataTimeToLive = ttl)
  def readBatchSize(s: Int):                  StorageBuilder = copy(readBatchSize = s)
  def spanCodec(c: Codec[gen.Span]):          StorageBuilder = copy(spanCodec = c)

  def apply() = {
    val keyspace = keyspaceBuilder.connect()
    val traces = keyspace.columnFamily(columnFamily, LongCodec, Utf8Codec, spanCodec)
      .consistency(writeConsistency)
      .consistency(readConsistency)

    CassandraStorage(keyspace, traces, readBatchSize, dataTimeToLive)
  }
}
