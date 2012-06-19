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

import com.twitter.zipkin.storage.cassandra.CassandraStorage
import com.twitter.zipkin.gen
import com.twitter.cassie.codecs.{Utf8Codec, LongCodec}
import com.twitter.cassie.{ReadConsistency, WriteConsistency}
import com.twitter.logging.Logger

trait CassandraStorageConfig extends StorageConfig {

  val log = Logger.get(getClass.getName)

  def cassandraConfig: CassandraConfig

  // this is how many traces we fetch from cassandra in one request
  var traceFetchBatchSize = 500

  var tracesCf               : String = "Traces"

  def apply(): CassandraStorage = {
    val _storageConfig = this
    val _keyspace = cassandraConfig.keyspace

    /**
     * Row key is the trace id.
     * Column name is the span identifier.
     * Value is a Thrift serialized Span.
     */
    val _traces = _keyspace.columnFamily[Long, String, gen.Span](tracesCf,
      LongCodec, Utf8Codec, cassandraConfig.spanCodec)
      .consistency(WriteConsistency.One)
      .consistency(ReadConsistency.One)

    new CassandraStorage() {
      val cassandraConfig = _storageConfig.cassandraConfig
      val storageConfig = _storageConfig
      keyspace = _keyspace
      val traces = _traces
    }
  }
}
