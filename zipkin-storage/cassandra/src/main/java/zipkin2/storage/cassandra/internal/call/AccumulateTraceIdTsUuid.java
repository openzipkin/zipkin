/*
 * Copyright 2015-2019 The OpenZipkin Authors
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
package zipkin2.storage.cassandra.internal.call;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.utils.UUIDs;
import java.util.AbstractMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class AccumulateTraceIdTsUuid
  extends AccumulateAllResults<Set<Map.Entry<String, Long>>> {

  @Override protected Supplier<Set<Map.Entry<String, Long>>> supplier() {
    return LinkedHashSet::new; // because results are not distinct
  }

  @Override protected BiConsumer<Row, Set<Map.Entry<String, Long>>> accumulator() {
    return (row, result) ->
      result.add(
        new AbstractMap.SimpleEntry<>(row.getString("trace_id"),
          UUIDs.unixTimestamp(row.getUUID("ts"))));
  }

  @Override public String toString() {
    return "AccumulateTraceIdTsUuid{}";
  }
}
