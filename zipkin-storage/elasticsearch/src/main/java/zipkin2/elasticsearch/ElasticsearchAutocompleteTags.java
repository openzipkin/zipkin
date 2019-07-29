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
package zipkin2.elasticsearch;

import java.util.List;
import zipkin2.Call;
import zipkin2.elasticsearch.internal.IndexNameFormatter;
import zipkin2.elasticsearch.internal.client.Aggregation;
import zipkin2.elasticsearch.internal.client.SearchCallFactory;
import zipkin2.elasticsearch.internal.client.SearchRequest;
import zipkin2.storage.AutocompleteTags;

final class ElasticsearchAutocompleteTags implements AutocompleteTags {
  static final String AUTOCOMPLETE = "autocomplete";

  final boolean enabled;
  final IndexNameFormatter indexNameFormatter;
  final SearchCallFactory search;
  final int namesLookback;
  final Call<List<String>> keysCall;

  ElasticsearchAutocompleteTags(ElasticsearchStorage es) {
    this.search = new SearchCallFactory(es.http());
    this.indexNameFormatter = es.indexNameFormatter();
    this.enabled = es.searchEnabled() && !es.autocompleteKeys().isEmpty();
    this.namesLookback = es.namesLookback();
    this.keysCall = Call.create(es.autocompleteKeys());
  }

  @Override public Call<List<String>> getKeys() {
    if (!enabled) return Call.emptyList();
    return keysCall.clone();
  }

  @Override public Call<List<String>> getValues(String key) {
    if (key == null) throw new NullPointerException("key == null");
    if (key.isEmpty()) throw new IllegalArgumentException("key was empty");
    if (!enabled) return Call.emptyList();

    long endMillis = System.currentTimeMillis();
    long beginMillis = endMillis - namesLookback;
    List<String> indices =
      indexNameFormatter.formatTypeAndRange(AUTOCOMPLETE, beginMillis, endMillis);

    if (indices.isEmpty()) return Call.emptyList();

    SearchRequest.Filters filters =
      new SearchRequest.Filters().addTerm("tagKey", key);

    SearchRequest request = SearchRequest.create(indices)
      .filters(filters)
      .addAggregation(Aggregation.terms("tagValue", Integer.MAX_VALUE));
    return search.newCall(request, BodyConverters.KEYS);
  }
}
