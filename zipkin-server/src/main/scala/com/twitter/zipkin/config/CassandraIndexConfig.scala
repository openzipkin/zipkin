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

import com.twitter.zipkin.storage.cassandra._
import com.twitter.cassie.codecs.{Utf8Codec, LongCodec}
import com.twitter.cassie.{ReadConsistency, WriteConsistency}
import com.twitter.logging.Logger

trait CassandraIndexConfig extends IndexConfig {

  val log = Logger.get(getClass.getName)

  def cassandraConfig: CassandraConfig

  // this is how many entries we fetch from cassandra in one call using the iterator
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


  def apply(): CassandraIndex = {

    val _keyspace = cassandraConfig.keyspace

    /**
     * Row key is the service.spanname.
     * Column name is the timestamp.
     * Value is the trace id.
     */
    val _serviceSpanNameIndex = _keyspace.columnFamily[String, Long, Long](serviceSpanNameIndexCf,
      Utf8Codec, LongCodec, LongCodec)
      .consistency(WriteConsistency.One)
      .consistency(ReadConsistency.One)

    /**
     * Row key is the service.
     * Column name is the timestamp.
     * Value is the trace id.
     */
    val _serviceNameIndex = new StringBucketedColumnFamily(
      BucketedColumnFamily(
        _keyspace,
        serviceNameIndexCf,
        LongCodec,
        LongCodec,
        WriteConsistency.One,
        ReadConsistency.One
      ),
      numBuckets
    )


    /**
     * Row key is "annotation value" (for time based annotations) or "annotation key:annotation value" for key value
     * based annotations.
     * Column name is the timestamp.
     * Value is the trace id.
     */
    val _annotationsIndex = new ByteBufferBucketedColumnFamily(
      BucketedColumnFamily(
        _keyspace,
        annotationsIndexCf,
        LongCodec,
        LongCodec,
        WriteConsistency.One,
        ReadConsistency.One
      ),
      numBuckets
    )

    /**
     * Row key is trace id
     * Column name is the timestamp of the span.
     * Value is not used
     */
    val _durationIndex = _keyspace.columnFamily[Long, Long, String](durationIndexCf,
      LongCodec, LongCodec, Utf8Codec)
      .consistency(WriteConsistency.One)
      .consistency(ReadConsistency.One)

    /**
     * Key is hardcoded string to look up by
     * Column is service names
     * Value is not used
     */
    val _serviceNames = new StringBucketedColumnFamily(
      BucketedColumnFamily(
        _keyspace,
        serviceNamesCf,
        Utf8Codec,
        Utf8Codec,
        WriteConsistency.One,
        ReadConsistency.One
      ),
      numBuckets
    )

    /**
     * Row key is service name.
     * Column name is span name (that is connected to the service).
     * Value is not used.
     */
    val _spanNames = new StringBucketedColumnFamily(
      BucketedColumnFamily(
        _keyspace,
        spanNamesCf,
        Utf8Codec,
        Utf8Codec,
        WriteConsistency.One,
        ReadConsistency.One
      ),
      numBuckets)

    log.info("Connected to Cassandra")
    new CassandraIndex() {
      val config               = cassandraConfig
      keyspace                 = _keyspace
      val serviceSpanNameIndex = _serviceSpanNameIndex
      val serviceNameIndex     = _serviceNameIndex
      val annotationsIndex     = _annotationsIndex
      val durationIndex        = _durationIndex
      val serviceNames         = _serviceNames
      val spanNames            = _spanNames
    }
  }
}
