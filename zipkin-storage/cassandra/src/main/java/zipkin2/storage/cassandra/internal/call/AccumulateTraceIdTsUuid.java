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
package zipkin2.storage.cassandra.internal.call;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.utils.UUIDs;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class AccumulateTraceIdTsUuid
  extends AccumulateAllResults<Map<String, Long>> {

  @Override protected Supplier<Map<String, Long>> supplier() {
    return LinkedHashMap::new; // because results are not distinct
  }

  @Override protected BiConsumer<Row, Map<String, Long>> accumulator() {
    return (row, result) ->
      result.put(row.getString("trace_id"), UUIDs.unixTimestamp(row.getUUID("ts")));
  }

  @Override public String toString() {
    return "AccumulateTraceIdTsUuid{}";
  }
}
