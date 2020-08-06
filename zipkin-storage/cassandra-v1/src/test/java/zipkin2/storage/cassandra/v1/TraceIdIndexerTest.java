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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import zipkin2.storage.cassandra.v1.IndexTraceId.Input;
import zipkin2.storage.cassandra.v1.TraceIdIndexer.Factory;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static zipkin2.storage.cassandra.v1.Tables.SERVICE_NAME_INDEX;

public class TraceIdIndexerTest {
  static final long NANOS_PER_SECOND = SECONDS.toNanos(1L);
  long nanoTime;
  Factory factory = new Factory(SERVICE_NAME_INDEX, NANOS_PER_SECOND, 10) {
    @Override long nanoTime() {
      return nanoTime;
    }
  };

  Input input1 = Input.create("app", 1467676800150000L, 1L);
  Input input2 = Input.create("web", 1467676800050000L, 1L);

  @Test void trimCache_expires() {
    TraceIdIndexer indexer = factory.newIndexer();

    nanoTime = NANOS_PER_SECOND;
    indexer.add(input1);
    indexer.iterator();
    assertThat(factory.cache).hasSize(1);

    nanoTime += NANOS_PER_SECOND / 2L;
    factory.trimCache();
    assertThat(factory.cache).hasSize(1);

    nanoTime += NANOS_PER_SECOND / 2L;
    factory.trimCache();
    assertThat(factory.cache).isEmpty();
  }

  @Test void trimCache_extends() {
    TraceIdIndexer indexer = factory.newIndexer();

    nanoTime = NANOS_PER_SECOND;
    indexer.add(input1);
    indexer.iterator();
    assertThat(factory.cache).hasSize(1);

    nanoTime += NANOS_PER_SECOND / 2L;
    factory.trimCache();
    assertThat(factory.cache).hasSize(1);

    // adding again extends the expiration
    indexer = factory.newIndexer();
    indexer.add(input1);
    indexer.iterator();

    nanoTime += NANOS_PER_SECOND / 2L;
    factory.trimCache();
    assertThat(factory.cache).hasSize(1);

    nanoTime += NANOS_PER_SECOND / 2L;
    factory.trimCache();
    assertThat(factory.cache).isEmpty();
  }

  @Test void keysAreIndependent() {
    TraceIdIndexer indexer = factory.newIndexer();

    nanoTime = NANOS_PER_SECOND;
    indexer.add(input1);
    indexer.add(input2);
    indexer.iterator();
    assertThat(factory.cache).hasSize(2);

    nanoTime += NANOS_PER_SECOND / 2L;
    factory.trimCache();
    assertThat(factory.cache).hasSize(2);

    // Different indexer touches input1, which extends its expiration, leaving index2 alone
    indexer = factory.newIndexer();
    indexer.add(input1);
    indexer.iterator();

    nanoTime += NANOS_PER_SECOND / 2L;
    factory.trimCache();
    assertThat(factory.cache).hasSize(1);

    nanoTime += NANOS_PER_SECOND / 2L;
    factory.trimCache();
    assertThat(factory.cache).isEmpty();
  }

  @Test void worksOnRollover() {
    TraceIdIndexer indexer = factory.newIndexer();

    nanoTime = -NANOS_PER_SECOND / 2L;
    indexer.add(input1);
    indexer.iterator();
    assertThat(factory.cache).hasSize(1);

    nanoTime = 0L;
    factory.trimCache();
    assertThat(factory.cache).hasSize(1);

    nanoTime = NANOS_PER_SECOND / 2L;
    factory.trimCache();
    assertThat(factory.cache).isEmpty();
  }

  @Test @Timeout(1000L) void cardinality() {
    long count = factory.cardinality * 10L;
    for (long i = 0L; i < count; i++, nanoTime++) {
      TraceIdIndexer indexer = factory.newIndexer();
      indexer.add(Input.create("app", 1467676800150000L, i));
      assertThat(indexer).hasSize(1);
    }

    assertThat(factory.cache)
      .doesNotContainKey(entry("app", 0L)) // eldest evicted
      .containsKey(entry("app", count - 1L)); // youngest not evicted

    factory.newIndexer(); // lazy cleanup as we will be 1 over otherwise

    // verify internal state
    assertThat(factory.cache)
      .hasSameSizeAs(factory.expirations)
      .hasSize(factory.cardinality);
  }

  @Test @Timeout(2000L) void cardinality_parallel() throws InterruptedException {
    AtomicLong trueCount = new AtomicLong();
    ExecutorService exec = Executors.newFixedThreadPool(4);

    long count = factory.cardinality * 10L;
    LongStream.range(0L, count).forEach(i -> exec.execute(() -> {
      TraceIdIndexer indexer = factory.newIndexer();
      indexer.add(Input.create("app", 1467676800150000L, i));

      // don't use assert as it will crash a thread not the test running it
      if (indexer.iterator().hasNext()) trueCount.incrementAndGet();
    }));

    exec.shutdown();
    assertThat(exec.awaitTermination(1L, SECONDS)).isTrue();

    assertThat(trueCount).hasValue(count);

    factory.newIndexer(); // lazy cleanup as we will be several over otherwise

    // verify internal state
    assertThat(factory.cache)
      .hasSameSizeAs(factory.expirations)
      .hasSize(factory.cardinality);
  }

  @Test void iterator_filtersEntriesWithinTraceInterval() {
    TraceIdIndexer indexer = factory.newIndexer();

    indexer.add(Input.create("app", 1467676800150000L, 1L));
    indexer.add(Input.create("web", 1467676800050000L, 1L));
    indexer.add(Input.create("app", 1467676800150000L, 2L));
    indexer.add(Input.create("app", 1467676800125000L, 1L));
    indexer.add(Input.create("app", 1467676800125000L, 2L));
    indexer.add(Input.create("app", 1467676800110000L, 1L));
    indexer.add(Input.create("db", 1467676800150000L, 1L));
    indexer.add(Input.create("web", 1467676800000000L, 1L));
    indexer.add(Input.create("web", 1467676800025000L, 1L));

    assertThat(indexer).containsExactlyInAnyOrder(
      Input.create("app", 1467676800110000L, 1L),
      Input.create("app", 1467676800150000L, 1L),
      Input.create("app", 1467676800125000L, 2L),
      Input.create("app", 1467676800150000L, 2L),
      Input.create("db", 1467676800150000L, 1L),
      Input.create("web", 1467676800000000L, 1L),
      Input.create("web", 1467676800050000L, 1L)
    );
  }
}
