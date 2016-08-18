/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import zipkin.Span;

import static zipkin.internal.Util.sortedList;

public class MergeById {

  public static List<Span> apply(Collection<Span> spans) {
    List<Span> result = new ArrayList<Span>(spans.size());
    Map<Long, List<Span>> spanIdToSpans = new LinkedHashMap<Long, List<Span>>();
    for (Span span : spans) {
      if (!spanIdToSpans.containsKey(span.id)) {
        spanIdToSpans.put(span.id, new LinkedList<Span>());
      }
      spanIdToSpans.get(span.id).add(span);
    }

    for (List<Span> spansToMerge : spanIdToSpans.values()) {
      if (spansToMerge.size() == 1) {
        result.add(spansToMerge.get(0));
      } else {
        Span.Builder builder = spansToMerge.get(0).toBuilder();
        for (int i = 1, length = spansToMerge.size(); i < length; i++) {
          builder.merge(spansToMerge.get(i));
        }
        result.add(builder.build());
      }
    }

    // Apply timestamp so that sorting will be helpful
    for (int i = 0; i < result.size(); i++) {
      result.set(i, ApplyTimestampAndDuration.apply(result.get(i)));
    }
    return sortedList(result);
  }

  private MergeById() {
  }
}
