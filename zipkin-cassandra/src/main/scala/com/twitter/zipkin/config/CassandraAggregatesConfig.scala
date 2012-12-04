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

import com.twitter.cassie.codecs.{LongCodec, Utf8Codec}
import com.twitter.cassie.{ReadConsistency, WriteConsistency}
import com.twitter.zipkin.storage.cassandra.CassandraAggregates

trait CassandraAggregatesConfig extends AggregatesConfig { self =>

  def cassandraConfig: CassandraConfig
  var topAnnotationsCf: String = "TopAnnotations"
  var dependenciesCf: String = "Dependencies"

  var writeConsistency: WriteConsistency = WriteConsistency.One
  var readConsistency: ReadConsistency = ReadConsistency.One

  def apply(): CassandraAggregates = {
    val keyspace = cassandraConfig.keyspace

    val topAnnotations = keyspace.columnFamily(topAnnotationsCf,Utf8Codec, LongCodec, Utf8Codec)
      .consistency(writeConsistency)
      .consistency(readConsistency)

    val dependencies = keyspace.columnFamily(dependenciesCf, Utf8Codec, LongCodec, Utf8Codec)
      .consistency(writeConsistency)
      .consistency(readConsistency)

    CassandraAggregates(keyspace, topAnnotations, dependencies)
  }
}
