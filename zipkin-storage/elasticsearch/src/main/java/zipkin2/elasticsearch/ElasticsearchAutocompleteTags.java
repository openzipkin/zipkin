/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch;

import java.util.List;
import zipkin2.Call;
import zipkin2.elasticsearch.internal.IndexNameFormatter;
import zipkin2.elasticsearch.internal.client.Aggregation;
import zipkin2.elasticsearch.internal.client.SearchCallFactory;
import zipkin2.elasticsearch.internal.client.SearchRequest;
import zipkin2.storage.AutocompleteTags;

import static zipkin2.elasticsearch.VersionSpecificTemplates.TYPE_AUTOCOMPLETE;

final class ElasticsearchAutocompleteTags implements AutocompleteTags {

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
      indexNameFormatter.formatTypeAndRange(TYPE_AUTOCOMPLETE, beginMillis, endMillis);

    if (indices.isEmpty()) return Call.emptyList();

    SearchRequest.Filters filters =
      new SearchRequest.Filters().addTerm("tagKey", key);

    SearchRequest request = SearchRequest.create(indices)
      .filters(filters)
      .addAggregation(Aggregation.terms("tagValue", Integer.MAX_VALUE));
    return search.newCall(request, BodyConverters.KEYS);
  }
}
