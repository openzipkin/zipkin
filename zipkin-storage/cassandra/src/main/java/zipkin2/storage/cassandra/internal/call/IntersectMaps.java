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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import zipkin2.Call;
import zipkin2.internal.AggregateCall;

public final class IntersectMaps extends AggregateCall<Map<String, Long>, Map<String, Long>> {

  public IntersectMaps(List<Call<Map<String, Long>>> calls) {
    super(calls);
  }

  @Override protected Map<String, Long> newOutput() {
    return new LinkedHashMap<>();
  }

  boolean firstInput = true;

  @Override protected void append(Map<String, Long> input, Map<String, Long> output) {
    if (firstInput) {
      firstInput = false;
      output.putAll(input);
    } else {
      output.keySet().retainAll(input.keySet());
    }
  }

  @Override protected boolean isEmpty(Map<String, Long> output) {
    return output.isEmpty();
  }

  @Override public IntersectMaps clone() {
    return new IntersectMaps(cloneCalls());
  }
}
