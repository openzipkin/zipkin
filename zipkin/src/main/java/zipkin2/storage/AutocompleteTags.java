/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
