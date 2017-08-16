/**
 * Copyright 2015-2017 The OpenZipkin Authors
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

import zipkin.internal.Util;

final class PartitionKeyToTraceId {

  final String table;
  final String partitionKey; // ends up as a partition key, ignoring bucketing
  final long traceId; // clustering key

  PartitionKeyToTraceId(String table, String partitionKey, long traceId) {
    this.table = table;
    this.partitionKey = partitionKey;
    this.traceId = traceId;
  }

  @Override public String toString() {
    return "(" + table + "," + partitionKey + "," + Util.toLowerHex(traceId) + ")";
  }

  @Override public boolean equals(Object o) {
    if (o == this) return true;
    if (o instanceof PartitionKeyToTraceId) {
      PartitionKeyToTraceId that = (PartitionKeyToTraceId) o;
      return this.table.equals(that.table)
          && this.partitionKey.equals(that.partitionKey)
          && this.traceId == that.traceId;
    }
    return false;
  }

  @Override public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= table.hashCode();
    h *= 1000003;
    h ^= partitionKey.hashCode();
    h *= 1000003;
    h ^= (int) (h ^ ((traceId >>> 32) ^ traceId));
    return h;
  }
}
