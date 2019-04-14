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

  @Override protected boolean isEmpty(Map<K, V> output) {
    return output.isEmpty();
  }

  @Override public IntersectMaps<K, V> clone() {
    return new IntersectMaps<>(cloneCalls());
  }
}
