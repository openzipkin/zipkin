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
