/*
 * Copyright 2015-2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.storage.cassandra.v1;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Select;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.in;
import static zipkin2.storage.cassandra.v1.IndexTraceId.BUCKETS;
import static zipkin2.storage.cassandra.v1.Tables.ANNOTATIONS_INDEX;

// select blobAsText(annotation),TOUNIXTIMESTAMP(ts),bigintAsBlob(trace_id) from annotations_index;
final class SelectTraceIdTimestampFromAnnotations extends SelectTraceIdIndex.Factory<String> {
  SelectTraceIdTimestampFromAnnotations(Session session) {
    super(session, ANNOTATIONS_INDEX, "annotation", 2);
  }

  @Override Select.Where declarePartitionKey(Select select) {
    return super
      .declarePartitionKey(select)
      .and(in("bucket", bindMarker()));
  }

  @Override void bindPartitionKey(BoundStatement bound, String partitionKey) {
    bound
      .setBytes(0, CassandraUtil.toByteBuffer(partitionKey))
      .setList(1, BUCKETS, Integer.class);
  }
}
