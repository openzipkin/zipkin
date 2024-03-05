/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.elasticsearch.internal;

import zipkin2.elasticsearch.ElasticsearchStorage;
import zipkin2.elasticsearch.internal.client.HttpCall;

/**
 * Escalate internal APIs so they can be used from outside packages. The only implementation is in
 * {@link ElasticsearchStorage}.
 *
 * <p>Inspired by {@code okhttp3.internal.Internal}.
 */
public abstract class Internal {
  public static Internal instance;

  public abstract HttpCall.Factory http(ElasticsearchStorage storage);
}
