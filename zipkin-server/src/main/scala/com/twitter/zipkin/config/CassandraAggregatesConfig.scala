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

import com.twitter.zipkin.storage.cassandra.CassandraAggregates
import com.twitter.cassie.codecs.{LongCodec, Utf8Codec}
import com.twitter.cassie.{ColumnFamily, ReadConsistency, WriteConsistency}

trait CassandraAggregatesConfig extends AggregatesConfig { self =>

  def cassandraConfig: CassandraConfig
  var topAnnotationsCf: String = "TopAnnotations"

  def apply(): CassandraAggregates = {
    val _topAnnotations = cassandraConfig.keyspace.columnFamily[String, Long, String](
      topAnnotationsCf,Utf8Codec, LongCodec, Utf8Codec
    ).consistency(WriteConsistency.One).consistency(ReadConsistency.One)

    new CassandraAggregates {
      val topAnnotations: ColumnFamily[String, Long, String] = _topAnnotations
    }
  }
}
