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
import com.twitter.cassie.{ReadConsistency, WriteConsistency}
import com.twitter.logging.Logger
import com.twitter.util.Duration
import com.twitter.zipkin.storage.cassandra._

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

    log.info("Connected to Cassandra")
    CassandraIndex(
      keyspace,
      serviceNamesCf, spanNamesCf, serviceNameIndexCf, serviceSpanNameIndexCf, annotationsIndexCf, durationIndexCf,
      dataTimeToLive,
      numBuckets,
      writeConsistency,
      readConsistency)
  }
}
