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

final class PartitionKeyToTraceId {

  final String table;
  final String partitionKey; // ends up as a partition key, ignoring bucketing
  final String traceId; // clustering key

  PartitionKeyToTraceId(String table, String partitionKey, String traceId) {
    this.table = table;
    this.partitionKey = partitionKey;
    this.traceId = lowerTraceId(traceId); // cassandra trace ID is lower 64 bits
  }

  static String lowerTraceId(String traceId) {
    return traceId.length() <= 16 ? traceId : traceId.substring(16);
  }

  @Override
  public String toString() {
    return "(" + table + "," + partitionKey + "," + traceId + ")";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) return true;
    if (o instanceof PartitionKeyToTraceId) {
      PartitionKeyToTraceId that = (PartitionKeyToTraceId) o;
      return this.table.equals(that.table)
          && this.partitionKey.equals(that.partitionKey)
          && this.traceId.equals(that.traceId);
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= table.hashCode();
    h *= 1000003;
    h ^= partitionKey.hashCode();
    h *= 1000003;
    h ^= traceId.hashCode();
    return h;
  }
}
