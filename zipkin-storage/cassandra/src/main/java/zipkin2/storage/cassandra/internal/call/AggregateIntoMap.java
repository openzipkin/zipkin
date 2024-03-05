/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra.internal.call;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import zipkin2.Call;
import zipkin2.internal.AggregateCall;

public final class AggregateIntoMap<K, V> extends AggregateCall<Map<K, V>, Map<K, V>> {
  public AggregateIntoMap(List<Call<Map<K, V>>> calls) {
    super(calls);
  }

  @Override protected Map<K, V> newOutput() {
    return new LinkedHashMap<>();
  }

  @Override protected void append(Map<K, V> input, Map<K, V> output) {
    output.putAll(input);
  }

  @Override public AggregateIntoMap<K, V> clone() {
    return new AggregateIntoMap<>(cloneCalls());
  }
}
