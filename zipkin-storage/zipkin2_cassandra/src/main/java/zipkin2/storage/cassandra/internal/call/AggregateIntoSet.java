/**
 * Copyright 2015-2017 The OpenZipkin Authors
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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import zipkin2.Call;

public final class AggregateIntoSet<T> extends AggregateCall<Set<T>, Set<T>> {
  public AggregateIntoSet(List<Call<Set<T>>> calls) {
    super(calls);
  }

  @Override protected Set<T> newOutput() {
    return new LinkedHashSet<>();
  }

  @Override protected void append(Set<T> input, Set<T> output) {
    output.addAll(input);
  }

  @Override protected boolean isEmpty(Set<T> output) {
    return output.isEmpty();
  }

  @Override public AggregateIntoSet<T> clone() {
    return new AggregateIntoSet<>(cloneCalls());
  }
}
