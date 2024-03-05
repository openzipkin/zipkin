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

public final class IntersectMaps<K, V> extends AggregateCall<Map<K, V>, Map<K, V>> {

  public IntersectMaps(List<Call<Map<K, V>>> calls) {
    super(calls);
  }

  @Override protected Map<K, V> newOutput() {
    return new LinkedHashMap<>();
  }

  boolean firstInput = true;

  @Override protected void append(Map<K, V> input, Map<K, V> output) {
    if (firstInput) {
      firstInput = false;
      output.putAll(input);
    } else {
      output.keySet().retainAll(input.keySet());
    }
  }

  @Override public IntersectMaps<K, V> clone() {
    return new IntersectMaps<>(cloneCalls());
  }
}
