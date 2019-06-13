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
package zipkin2.storage;

import java.util.List;
import zipkin2.Call;

/**
 * Provides autocomplete functionality by providing values for a given tag key, usually derived from
 * {@link SpanConsumer}.
 */
public interface AutocompleteTags {

  /**
   * Retrieves the list of tag getKeys whose values may be returned by {@link #getValues(String)}.
   *
   * @see StorageComponent.Builder#autocompleteKeys(List)
   */
  Call<List<String>> getKeys();

  /**
   * Retrieves the list of values, if the input is configured for autocompletion. If a key is not
   * configured, or there are no values available, an empty result will be returned.
   *
   * @throws IllegalArgumentException if the input is empty.
   * @see StorageComponent.Builder#autocompleteKeys(List)
   */
  Call<List<String>> getValues(String key);
}
