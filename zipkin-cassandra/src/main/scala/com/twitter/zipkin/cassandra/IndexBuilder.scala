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

import com.twitter.cassie.codecs.{LongCodec, Utf8Codec}
import com.twitter.cassie.{ColumnFamily, ReadConsistency, WriteConsistency, KeyspaceBuilder}
import com.twitter.conversions.time._
import com.twitter.util.Duration
import com.twitter.zipkin.builder.Builder
import com.twitter.zipkin.storage.cassandra.{ByteBufferBucketedColumnFamily, BucketedColumnFamily, StringBucketedColumnFamily, CassandraIndex}
import com.twitter.zipkin.storage.Index
import java.nio.ByteBuffer

case class IndexBuilder(
  keyspaceBuilder: KeyspaceBuilder,
  serviceNamesCf: String         = "ServiceNames",
  spanNamesCf: String            = "SpanNames",
  serviceNameIndexCf: String     = "ServiceNameIndex",
  serviceSpanNameIndexCf: String = "ServiceSpanNameIndex",
  annotationsIndexCf: String     = "AnnotationsIndex",
  durationIndexCf: String        = "DurationIndex",
  dataTimeToLive: Duration = 3.days,
  numBuckets: Int = 10,
  writeConsistency: WriteConsistency = WriteConsistency.One,
  readConsistency: ReadConsistency = ReadConsistency.One
) extends Builder[Index] {

  def serviceNamesCf(s: String):              IndexBuilder = copy(serviceNamesCf = s)
  def spanNamesCf(s: String):                 IndexBuilder = copy(spanNamesCf = s)
  def serviceNameIndexCf(s: String):          IndexBuilder = copy(serviceNameIndexCf = s)
  def serviceSpanNameIndexCf(s: String):      IndexBuilder = copy(serviceSpanNameIndexCf = s)
  def annotationsIndexCf(s: String):          IndexBuilder = copy(annotationsIndexCf = s)
  def durationIndexCf(s: String):             IndexBuilder = copy(durationIndexCf = s)
  def dataTimeToLive(d: Duration):            IndexBuilder = copy(dataTimeToLive = d)
  def numBuckets(n: Int):                     IndexBuilder = copy(numBuckets = n)
  def writeConsistency(wc: WriteConsistency): IndexBuilder = copy(writeConsistency = wc)
  def readConsistency(rc: ReadConsistency):   IndexBuilder = copy(readConsistency = rc)

  def apply() = {

    val keyspace = keyspaceBuilder.connect()

    /**
     * Key is hardcoded string to look up by
     * Column is service names
     * Value is not used
     */
    val serviceNames: ColumnFamily[String, String, String] = new StringBucketedColumnFamily(
      BucketedColumnFamily(
        keyspace,
        serviceNamesCf,
        Utf8Codec,
        Utf8Codec,
        writeConsistency,
        readConsistency
      ),
      numBuckets
    )

    /**
     * Row key is service name.
     * Column name is span name (that is connected to the service).
     * Value is not used.
     */
    val spanNames: ColumnFamily[String, String, String] = new StringBucketedColumnFamily(
      BucketedColumnFamily(
        keyspace,
        spanNamesCf,
        Utf8Codec,
        Utf8Codec,
        writeConsistency,
        readConsistency
      ),
      numBuckets)

    /**
     * Row key is the service.
     * Column name is the timestamp.
     * Value is the trace id.
     */
    val serviceNameIndex: ColumnFamily[String, Long, Long] = new StringBucketedColumnFamily(
      BucketedColumnFamily(
        keyspace,
        serviceNameIndexCf,
        LongCodec,
        LongCodec,
        writeConsistency,
        readConsistency
      ),
      numBuckets
    )

    /**
     * Row key is the service.spanname.
     * Column name is the timestamp.
     * Value is the trace id.
     */
    val serviceSpanNameIndex = keyspace.columnFamily(serviceSpanNameIndexCf, Utf8Codec, LongCodec, LongCodec)
      .consistency(writeConsistency)
      .consistency(readConsistency)

    /**
     * Row key is "annotation value" (for time based annotations) or "annotation key:annotation value" for key value
     * based annotations.
     * Column name is the timestamp.
     * Value is the trace id.
     */
    val annotationsIndex: ColumnFamily[ByteBuffer, Long, Long] = new ByteBufferBucketedColumnFamily(
      BucketedColumnFamily(
        keyspace,
        annotationsIndexCf,
        LongCodec,
        LongCodec,
        writeConsistency,
        readConsistency
      ),
      numBuckets
    )

    /**
     * Row key is trace id
     * Column name is the timestamp of the span.
     * Value is not used
     */
    val durationIndex = keyspace.columnFamily(durationIndexCf, LongCodec, LongCodec, Utf8Codec)
      .consistency(writeConsistency)
      .consistency(readConsistency)

    CassandraIndex(
      keyspace,
      serviceNames,
      spanNames,
      serviceNameIndex,
      serviceSpanNameIndex,
      annotationsIndex,
      durationIndex,
      dataTimeToLive)
  }
}
