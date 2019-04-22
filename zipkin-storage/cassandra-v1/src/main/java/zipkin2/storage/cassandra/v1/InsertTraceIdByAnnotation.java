/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import zipkin2.Span;

import static zipkin2.storage.cassandra.v1.CassandraUtil.annotationKeys;
import static zipkin2.storage.cassandra.v1.CassandraUtil.toByteBuffer;

// QueryRequest.annotations/binaryAnnotations
final class InsertTraceIdByAnnotation implements Indexer.IndexSupport {
  private static final ThreadLocalRandom RAND = ThreadLocalRandom.current();
  private final int bucketCount;

  InsertTraceIdByAnnotation(int bucketCount) {
    this.bucketCount = bucketCount;
  }

  @Override
  public String table() {
    return Tables.ANNOTATIONS_INDEX;
  }

  @Override
  public Insert declarePartitionKey(Insert insert) {
    return insert
        .value("annotation", QueryBuilder.bindMarker("annotation"))
        .value("bucket", QueryBuilder.bindMarker("bucket"));
  }

  @Override
  public BoundStatement bindPartitionKey(BoundStatement bound, String partitionKey) {
    return bound
        .setInt("bucket", RAND.nextInt(bucketCount))
        .setBytes("annotation", toByteBuffer(partitionKey));
  }

  @Override
  public Set<String> partitionKeys(Span span) {
    return annotationKeys(span);
  }
}
