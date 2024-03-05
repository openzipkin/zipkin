/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.cassandra;

import java.util.List;
import zipkin2.Call;
import zipkin2.storage.AutocompleteTags;

class CassandraAutocompleteTags implements AutocompleteTags {
  final boolean enabled;
  final Call<List<String>> keysCall;
  final SelectAutocompleteValues.Factory valuesCallFactory;

  CassandraAutocompleteTags(CassandraStorage storage) {
    enabled = storage.searchEnabled
      && !storage.autocompleteKeys.isEmpty()
      && storage.metadata().hasAutocompleteTags;
    keysCall = Call.create(List.copyOf(storage.autocompleteKeys));
    valuesCallFactory = enabled ? new SelectAutocompleteValues.Factory(storage.session()) : null;
  }

  @Override public Call<List<String>> getKeys() {
    if (!enabled) return Call.emptyList();
    return keysCall.clone();
  }

  @Override public Call<List<String>> getValues(String key) {
    if (key == null) throw new NullPointerException("key == null");
    if (key.isEmpty()) throw new IllegalArgumentException("key was empty");
    if (!enabled) return Call.emptyList();
    return valuesCallFactory.create(key);
  }
}
