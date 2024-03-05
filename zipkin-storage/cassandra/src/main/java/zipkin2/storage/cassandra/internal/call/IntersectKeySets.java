/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra.internal.call;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import zipkin2.Call;
import zipkin2.internal.AggregateCall;

public final class IntersectKeySets extends AggregateCall<Map<String, Long>, Set<String>> {
  public IntersectKeySets(List<Call<Map<String, Long>>> calls) {
    super(calls);
  }

  @Override protected Set<String> newOutput() {
    return new LinkedHashSet<>();
  }

  boolean firstInput = true;

  @Override protected void append(Map<String, Long> input, Set<String> output) {
    if (firstInput) {
      firstInput = false;
      output.addAll(input.keySet());
    } else {
      output.retainAll(input.keySet());
    }
  }

  @Override public IntersectKeySets clone() {
    return new IntersectKeySets(cloneCalls());
  }
}
