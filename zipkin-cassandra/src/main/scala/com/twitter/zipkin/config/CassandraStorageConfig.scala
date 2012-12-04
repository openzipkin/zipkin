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

import com.twitter.cassie.codecs.{Codec, Utf8Codec, LongCodec}
import com.twitter.cassie.{ReadConsistency, WriteConsistency}
import com.twitter.conversions.time._
import com.twitter.logging.Logger
import com.twitter.util.Duration
import com.twitter.zipkin.gen
import com.twitter.zipkin.storage.cassandra.{ScroogeThriftCodec, SnappyCodec, CassandraStorage}

trait CassandraStorageConfig extends StorageConfig {

  val log = Logger.get(getClass.getName)

  def cassandraConfig: CassandraConfig

  // this is how many traces we fetch from cassandra in one request
  var traceFetchBatchSize = 500

  var tracesCf               : String = "Traces"
  var writeConsistency = WriteConsistency.One
  var readConsistency = ReadConsistency.One
  var dataTimeToLive: Duration = 3.days
  var spanCodec: Codec[gen.Span] = new SnappyCodec(new ScroogeThriftCodec[gen.Span](gen.Span))

  def apply(): CassandraStorage = {
    val keyspace = cassandraConfig.keyspace

    /**
     * Row key is the trace id.
     * Column name is the span identifier.
     * Value is a Thrift serialized Span.
     */
    val traces = keyspace.columnFamily(tracesCf, LongCodec, Utf8Codec, spanCodec)
      .consistency(writeConsistency)
      .consistency(readConsistency)

    CassandraStorage(keyspace, traces, traceFetchBatchSize, dataTimeToLive)
  }
}
