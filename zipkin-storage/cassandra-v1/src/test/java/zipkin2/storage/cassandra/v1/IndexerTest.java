/*
 * Copyright 2015-2020 The OpenZipkin Authors
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
package zipkin2.storage.cassandra.v1;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;
import zipkin2.storage.cassandra.v1.Indexer.SetMultimap;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.storage.cassandra.v1.Tables.SERVICE_NAME_INDEX;
import static zipkin2.storage.cassandra.v1.Tables.SERVICE_SPAN_NAME_INDEX;

public class IndexerTest {

  @Test void entriesThatIncreaseGap_filtersEntriesWithinTraceInterval() {
    ConcurrentMap<PartitionKeyToTraceId, Pair> sharedState = new ConcurrentHashMap<>();

    SetMultimap<PartitionKeyToTraceId, Long> parsed = new SetMultimap<>(); // intentionally shuffled
    parsed.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "a"), 1467676800050L);
    parsed.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "a"), 1467676800150L);
    parsed.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "web", "a"), 1467676800050L);
    parsed.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "b"), 1467676800150L);
    parsed.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "a"), 1467676800125L);
    parsed.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "b"), 1467676800125L);
    parsed.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "a"), 1467676800110L);
    parsed.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "db", "a"), 1467676800150L);
    parsed.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "web", "a"), 1467676800000L);
    parsed.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "web", "a"), 1467676800025L);

    SetMultimap<PartitionKeyToTraceId, Long> expected = new SetMultimap<>();
    expected.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "a"), 1467676800050L);
    expected.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "a"), 1467676800150L);
    expected.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "b"), 1467676800125L);
    expected.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "b"), 1467676800150L);
    expected.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "db", "a"), 1467676800150L);
    expected.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "web", "a"), 1467676800000L);
    expected.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "web", "a"), 1467676800050L);

    assertThat(Indexer.entriesThatIncreaseGap(sharedState, parsed).delegate)
      .isEqualTo(expected.delegate);
  }

  /**
   * Most partition keys will not clash, as they are delimited differently. For example, spans index
   * partition keys are delimited with dots, and annotations with colons.
   *
   * <p>This tests an edge case, where a delimiter exists in a service name.
   */
  @Test void entriesThatIncreaseGap_treatsIndexesSeparately() {
    ConcurrentMap<PartitionKeyToTraceId, Pair> sharedState = new ConcurrentHashMap<>();

    // If indexes were not implemented properly, the span index app.foo would be mistaken as the
    // first service index
    SetMultimap<PartitionKeyToTraceId, Long> parsed = new SetMultimap<>();
    parsed.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app.foo", "a"), 1467676800050L);
    parsed.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app.foo", "a"), 1467676800110L);
    parsed.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app.foo", "a"), 1467676800125L);
    parsed.put(new PartitionKeyToTraceId(SERVICE_SPAN_NAME_INDEX, "app.foo", "a"), 1467676800000L);

    SetMultimap<PartitionKeyToTraceId, Long> expected = new SetMultimap<>();
    expected.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app.foo", "a"), 1467676800050L);
    expected.put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app.foo", "a"), 1467676800125L);
    expected.put(new PartitionKeyToTraceId(SERVICE_SPAN_NAME_INDEX, "app.foo", "a"),
      1467676800000L);

    assertThat(Indexer.entriesThatIncreaseGap(sharedState, parsed).delegate)
      .isEqualTo(expected.delegate);
  }
}
