/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage.mysql.v1;

import java.util.LinkedHashSet;
import java.util.List;
import zipkin2.Call;
import zipkin2.storage.AutocompleteTags;

final class MySQLAutocompleteTags implements AutocompleteTags {
  final DataSourceCall.Factory dataSourceCallFactory;
  final Schema schema;
  final boolean enabled;
  final LinkedHashSet<String> autocompleteKeys;
  final Call<List<String>> keysCall;

  MySQLAutocompleteTags(MySQLStorage storage, Schema schema) {
    this.dataSourceCallFactory = storage.dataSourceCallFactory;
    this.schema = schema;
    enabled = storage.searchEnabled && !storage.autocompleteKeys.isEmpty();
    autocompleteKeys = new LinkedHashSet<>(storage.autocompleteKeys);
    keysCall = Call.create(storage.autocompleteKeys);
  }

  @Override public Call<List<String>> getKeys() {
    if (!enabled) return Call.emptyList();
    return keysCall.clone();
  }

  @Override public Call<List<String>> getValues(String key) {
    if (key == null) throw new NullPointerException("key == null");
    if (key.isEmpty()) throw new IllegalArgumentException("key was empty");
    if (!enabled || !autocompleteKeys.contains(key)) return Call.emptyList();
    return dataSourceCallFactory.create(new SelectAutocompleteValues(schema, key));
  }
}
