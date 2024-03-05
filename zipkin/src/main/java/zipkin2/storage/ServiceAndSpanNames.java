/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.storage;

import java.util.List;
import zipkin2.Call;
import zipkin2.Endpoint;
import zipkin2.Span;

/**
 * Provides autocomplete functionality by providing values for service and span names, usually
 * derived from {@link SpanConsumer}.
 */
public interface ServiceAndSpanNames {

  /**
   * Retrieves all {@link Span#localEndpoint() local} {@link Endpoint#serviceName() service names},
   * sorted lexicographically.
   */
  Call<List<String>> getServiceNames();

  /**
   * Retrieves all {@link Span#remoteEndpoint() remote} {@link Endpoint#serviceName() service names}
   * recorded by a {@link Span#localEndpoint() service}, sorted lexicographically.
   */
  Call<List<String>> getRemoteServiceNames(String serviceName);

  /**
   * Retrieves all {@link Span#name() span names} recorded by a {@link Span#localEndpoint()
   * service}, sorted lexicographically.
   */
  Call<List<String>> getSpanNames(String serviceName);
}
