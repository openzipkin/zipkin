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
package com.twitter.zipkin.config

import com.twitter.conversions.time._
import com.twitter.cassie.codecs.{LongCodec, Utf8Codec}
import com.twitter.cassie.{ColumnFamily, ReadConsistency, WriteConsistency}
import com.twitter.logging.Logger
import com.twitter.util.Duration
import com.twitter.zipkin.storage.cassandra._
import java.nio.ByteBuffer

trait CassandraIndexConfig extends IndexConfig {

  val log = Logger.get(getClass.getName)

  def cassandraConfig: CassandraConfig

  // this is how many entries we fetch from cassandra in one call using the iterator
  var dataTimeToLive: Duration = 3.days
  var indexIteratorBatchSize = 500

  /* Cassandra keyspace and column family names */

  var serviceNamesCf         : String = "ServiceNames"
  var spanNamesCf            : String = "SpanNames"
  var serviceNameIndexCf     : String = "ServiceNameIndex"
  var serviceSpanNameIndexCf : String = "ServiceSpanNameIndex"
  var annotationsIndexCf     : String = "AnnotationsIndex"
  var durationIndexCf        : String = "DurationIndex"

  /* Max buckets for BucketedColumnFamily */
  var numBuckets: Int = 10

  var writeConsistency = WriteConsistency.One
  var readConsistency = ReadConsistency.One

  def apply(): CassandraIndex = {

    val keyspace = cassandraConfig.keyspace

    /**
     * Row key is the service.spanname.
     * Column name is the timestamp.
     * Value is the trace id.
     */
    lazy val serviceSpanNameIndex = keyspace.columnFamily(serviceSpanNameIndexCf, Utf8Codec, LongCodec, LongCodec)
      .consistency(writeConsistency)
      .consistency(readConsistency)

    /**
     * Row key is the service.
     * Column name is the timestamp.
     * Value is the trace id.
     */
    lazy val serviceNameIndex: ColumnFamily[String, Long, Long] = new StringBucketedColumnFamily(
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
     * Row key is "annotation value" (for time based annotations) or "annotation key:annotation value" for key value
     * based annotations.
     * Column name is the timestamp.
     * Value is the trace id.
     */
    lazy val annotationsIndex: ColumnFamily[ByteBuffer, Long, Long] = new ByteBufferBucketedColumnFamily(
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
    lazy val durationIndex = keyspace.columnFamily(durationIndexCf, LongCodec, LongCodec, Utf8Codec)
      .consistency(writeConsistency)
      .consistency(readConsistency)

    /**
     * Key is hardcoded string to look up by
     * Column is service names
     * Value is not used
     */
    lazy val serviceNames: ColumnFamily[String, String, String] = new StringBucketedColumnFamily(
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
    lazy val spanNames: ColumnFamily[String, String, String] = new StringBucketedColumnFamily(
      BucketedColumnFamily(
        keyspace,
        spanNamesCf,
        Utf8Codec,
        Utf8Codec,
        writeConsistency,
        readConsistency
      ),
      numBuckets)

    log.info("Connected to Cassandra")
    CassandraIndex(
      keyspace,
      serviceNames, spanNames, serviceNameIndex, serviceSpanNameIndex, annotationsIndex, durationIndex,
      dataTimeToLive,
      numBuckets,
      writeConsistency,
      readConsistency)
  }
}
