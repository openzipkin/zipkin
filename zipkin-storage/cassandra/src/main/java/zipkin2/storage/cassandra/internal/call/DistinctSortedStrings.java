/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra.internal.call;

import com.datastax.oss.driver.api.core.cql.Row;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class DistinctSortedStrings extends AccumulateAllResults<List<String>> {
  static final AccumulateAllResults<List<String>> INSTANCE = new DistinctSortedStrings();

  public static AccumulateAllResults<List<String>> get() {
    return INSTANCE;
  }

  @Override protected Supplier<List<String>> supplier() {
    return ArrayList::new;
  }

  @Override protected Function<List<String>, List<String>> finisher() {
    return SortDistinct.INSTANCE;
  }

  enum SortDistinct implements Function<List<String>, List<String>> {
    INSTANCE;

    @Override public List<String> apply(List<String> strings) {
      Collections.sort(strings);
      return new ArrayList<>(new LinkedHashSet<>(strings));
    }
  }

  @Override protected BiConsumer<Row, List<String>> accumulator() {
    return (row, list) -> {
      String result = row.getString(0);
      if (!result.isEmpty()) list.add(result);
    };
  }

  @Override public String toString() {
    return "DistinctSortedStrings{}";
  }

  DistinctSortedStrings() {
  }
}
