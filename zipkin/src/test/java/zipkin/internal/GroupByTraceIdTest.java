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
import java.util.List;
import org.junit.Test;
import zipkin.Span;
import zipkin.TestObjects;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static zipkin.TestObjects.LOTS_OF_SPANS;

public class GroupByTraceIdTest {

  @Test
  public void sortsDescending() {
    Span span1 = LOTS_OF_SPANS[0].toBuilder().timestamp(1L).build();
    Span span2 = LOTS_OF_SPANS[1].toBuilder().timestamp(2L).build();

    assertThat(GroupByTraceId.apply(asList(span1, span2), false, false))
        .containsExactly(asList(span2), asList(span1));
  }

  @Test
  public void noop_whenMixedAndNeitherStrictNorAdjusting() {
    List<Span> trace = new ArrayList<>(TestObjects.TRACE);
    trace.set(0, trace.get(0).toBuilder().traceIdHigh(1).build());
    // pretend the others downgraded to 64-bit trace IDs

    assertThat(GroupByTraceId.apply(trace, false, false))
        .containsExactly(trace);
  }

  @Test
  public void adjusts() {
    assertThat(GroupByTraceId.apply(TestObjects.TRACE, false, true))
        .containsExactly(CorrectForClockSkew.apply(MergeById.apply(TestObjects.TRACE)));
  }

  @Test
  public void groupsWhenStrict() {
    List<Span> trace = new ArrayList<>(TestObjects.TRACE);
    trace.set(0, trace.get(0).toBuilder().traceIdHigh(1).build());
    // pretend the others downgraded to 64-bit trace IDs

    assertThat(GroupByTraceId.apply(trace, true, false))
        .containsExactly(asList(trace.get(1), trace.get(2)), asList(trace.get(0)));
  }
}
