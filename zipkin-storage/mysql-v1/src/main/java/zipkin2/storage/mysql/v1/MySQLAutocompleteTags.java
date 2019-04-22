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
