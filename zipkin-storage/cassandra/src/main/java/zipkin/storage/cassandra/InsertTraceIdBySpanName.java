/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.storage.cassandra;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.Set;
import zipkin.Span;

// QueryRequest.spanName
final class InsertTraceIdBySpanName implements Indexer.IndexSupport {

  @Override public String table() {
    return Tables.SERVICE_SPAN_NAME_INDEX;
  }

  @Override public Insert declarePartitionKey(Insert insert) {
    return insert.value("service_span_name", QueryBuilder.bindMarker("service_span_name"));
  }

  @Override
  public BoundStatement bindPartitionKey(BoundStatement bound, String partitionKey) {
    return bound.setString("service_span_name", partitionKey);
  }

  @Override
  public Set<String> partitionKeys(Span span) {
    if (span.name.isEmpty()) return Collections.emptySet();

    ImmutableSet.Builder<String> result = ImmutableSet.builder();
    for (String serviceName : span.serviceNames()) {
      result.add(serviceName + "." + span.name);
    }
    return result.build();
  }
}
