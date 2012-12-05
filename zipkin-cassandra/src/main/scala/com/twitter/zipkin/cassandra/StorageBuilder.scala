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
