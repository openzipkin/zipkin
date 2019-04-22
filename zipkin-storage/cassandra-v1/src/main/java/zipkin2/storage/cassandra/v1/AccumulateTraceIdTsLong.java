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

import com.datastax.driver.core.Row;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import zipkin2.storage.cassandra.internal.call.AccumulateAllResults;

final class AccumulateTraceIdTsLong extends AccumulateAllResults<Set<Pair>> {
  final TimestampCodec timestampCodec;

  AccumulateTraceIdTsLong(TimestampCodec timestampCodec) {
    this.timestampCodec = timestampCodec;
  }

  @Override
  protected Supplier<Set<Pair>> supplier() {
    return LinkedHashSet::new; // because results are not distinct
  }

  @Override
  protected BiConsumer<Row, Set<Pair>> accumulator() {
    return (row, result) ->
        result.add(new Pair(row.getLong("trace_id"), timestampCodec.deserialize(row, "ts")));
  }

  @Override
  public String toString() {
    return "AccumulateTraceIdTsLong{}";
  }
}
