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
package zipkin.storage.elasticsearch;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeBucketsTest {

  @Rule
  public MockitoRule mocks = MockitoJUnit.rule();

  @Mock
  private SearchResponse response;

  @Mock
  private Aggregations aggregations;

  @Test
  public void emptyWhenAggregatesAreNull() {
    assertThat(new NativeClient.NativeBuckets(response).getBucketKeys("foo"))
        .isEmpty();
  }

  @Test
  public void emptyWhenMissingNameAgg() {
    when(response.getAggregations()).thenReturn(aggregations);

    assertThat(new NativeClient.NativeBuckets(response).getBucketKeys("foo"))
        .isEmpty();
  }

  @Test
  public void namesAggBucketKeysAreSpanNames() {
    when(response.getAggregations()).thenReturn(aggregations);
    Terms terms = mock(Terms.class);
    when(aggregations.get("name_agg")).thenReturn(terms);
    Terms.Bucket bucket = mock(Terms.Bucket.class);
    when(terms.getBuckets()).thenReturn(asList(bucket));
    when(bucket.getKeyAsString()).thenReturn("service");

    assertThat(new NativeClient.NativeBuckets(response).getBucketKeys("name_agg"))
        .containsExactly("service");
  }
}
