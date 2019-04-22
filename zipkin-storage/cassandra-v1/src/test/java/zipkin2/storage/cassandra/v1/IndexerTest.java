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

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Maps;
import java.util.concurrent.ConcurrentMap;
import org.junit.Test;

import static org.assertj.guava.api.Assertions.assertThat;
import static zipkin2.storage.cassandra.v1.Tables.SERVICE_NAME_INDEX;
import static zipkin2.storage.cassandra.v1.Tables.SERVICE_SPAN_NAME_INDEX;

public class IndexerTest {

  @Test
  public void entriesThatIncreaseGap_filtersEntriesWithinTraceInterval() {
    ConcurrentMap<PartitionKeyToTraceId, Pair> sharedState = Maps.newConcurrentMap();

    ImmutableSetMultimap<PartitionKeyToTraceId, Long> parsed = // intentionally shuffled
        ImmutableSetMultimap.<PartitionKeyToTraceId, Long>builder()
            .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "a"), 1467676800050L)
            .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "a"), 1467676800150L)
            .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "web", "a"), 1467676800050L)
            .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "b"), 1467676800150L)
            .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "a"), 1467676800125L)
            .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "b"), 1467676800125L)
            .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "a"), 1467676800110L)
            .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "db", "a"), 1467676800150L)
            .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "web", "a"), 1467676800000L)
            .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "web", "a"), 1467676800025L)
            .build();

    assertThat(Indexer.entriesThatIncreaseGap(sharedState, parsed))
        .hasSameEntriesAs(
            ImmutableSetMultimap.<PartitionKeyToTraceId, Long>builder()
                .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "a"), 1467676800050L)
                .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "a"), 1467676800150L)
                .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "b"), 1467676800125L)
                .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app", "b"), 1467676800150L)
                .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "db", "a"), 1467676800150L)
                .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "web", "a"), 1467676800000L)
                .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "web", "a"), 1467676800050L)
                .build());
  }

  /**
   * Most partition keys will not clash, as they are delimited differently. For example, spans index
   * partition keys are delimited with dots, and annotations with colons.
   *
   * <p>This tests an edge case, where a delimiter exists in a service name.
   */
  @Test
  public void entriesThatIncreaseGap_treatsIndexesSeparately() {
    ConcurrentMap<PartitionKeyToTraceId, Pair> sharedState = Maps.newConcurrentMap();

    // If indexes were not implemented properly, the span index app.foo would be mistaken as the
    // first service index
    ImmutableSetMultimap<PartitionKeyToTraceId, Long> parsed =
        ImmutableSetMultimap.<PartitionKeyToTraceId, Long>builder()
            .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app.foo", "a"), 1467676800050L)
            .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app.foo", "a"), 1467676800110L)
            .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app.foo", "a"), 1467676800125L)
            .put(new PartitionKeyToTraceId(SERVICE_SPAN_NAME_INDEX, "app.foo", "a"), 1467676800000L)
            .build();

    assertThat(Indexer.entriesThatIncreaseGap(sharedState, parsed))
        .hasSameEntriesAs(
            ImmutableSetMultimap.<PartitionKeyToTraceId, Long>builder()
                .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app.foo", "a"), 1467676800050L)
                .put(new PartitionKeyToTraceId(SERVICE_NAME_INDEX, "app.foo", "a"), 1467676800125L)
                .put(
                    new PartitionKeyToTraceId(SERVICE_SPAN_NAME_INDEX, "app.foo", "a"),
                    1467676800000L)
                .build());
  }
}
