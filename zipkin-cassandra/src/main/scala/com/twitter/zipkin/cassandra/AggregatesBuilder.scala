package com.twitter.zipkin.cassandra

import com.twitter.cassie.codecs.{LongCodec, Utf8Codec}
import com.twitter.cassie.{ReadConsistency, WriteConsistency, KeyspaceBuilder}
import com.twitter.zipkin.storage.cassandra.CassandraAggregates
import com.twitter.zipkin.storage.Aggregates
import com.twitter.util.Config

case class AggregatesBuilder(
  keyspaceBuilder: KeyspaceBuilder,
  topAnnotationsCf: String = "TopAnnotations",
  dependenciesCf: String = "Dependencies",
  writeConsistency: WriteConsistency = WriteConsistency.One,
  readConsistency: ReadConsistency = ReadConsistency.One
) extends Config[Aggregates] {

  def topAnnotationsCf(t: String):            AggregatesBuilder = copy(topAnnotationsCf = t)
  def dependenciesCf(d: String):              AggregatesBuilder = copy(dependenciesCf = d)
  def writeConsistency(wc: WriteConsistency): AggregatesBuilder = copy(writeConsistency = wc)
  def readConsistency(rc: ReadConsistency):   AggregatesBuilder = copy(readConsistency = rc)

  def apply() = {
    val keyspace = keyspaceBuilder.connect()

    val topAnnotations = keyspace.columnFamily(topAnnotationsCf,Utf8Codec, LongCodec, Utf8Codec)
      .consistency(writeConsistency)
      .consistency(readConsistency)

    val dependencies = keyspace.columnFamily(dependenciesCf, Utf8Codec, LongCodec, Utf8Codec)
      .consistency(writeConsistency)
      .consistency(readConsistency)

    CassandraAggregates(keyspace, topAnnotations, dependencies)
  }
}
