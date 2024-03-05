/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra.internal.call;

import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class AccumulateTraceIdTsUuid
  extends AccumulateAllResults<Map<String, Long>> {
  static final AccumulateAllResults<Map<String, Long>> INSTANCE = new AccumulateTraceIdTsUuid();

  public static AccumulateAllResults<Map<String, Long>> get() {
    return INSTANCE;
  }

  @Override protected Supplier<Map<String, Long>> supplier() {
    return LinkedHashMap::new; // because results are not distinct
  }

  @Override protected BiConsumer<Row, Map<String, Long>> accumulator() {
    return (row, result) ->
      result.put(row.getString(0), Uuids.unixTimestamp(row.getUuid(1)));
  }

  @Override public String toString() {
    return "AccumulateTraceIdTsUuid{}";
  }
}
