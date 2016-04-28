/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin;

/**
 * A component that provides storage interfaces used for spans and aggregations. Implementations are
 * free to provide other interfaces, but the ones declared here must be supported.
 *
 * <p>This component is lazy with regards to I/O. It can be injected directly to other components so
 * as to avoid crashing the application graph if the storage backend is unavailable.
 *
 * @see InMemoryStorage
 */
public interface StorageComponent extends AutoCloseable {

  SpanStore spanStore();

  AsyncSpanStore asyncSpanStore();

  /**
   * Used to store spans received on a transport such as Kafka. The provided consumer will retain
   * spans according to the sampler's policy. Unsampled spans will {@link
   * CollectorMetrics#incrementSpansDropped(int) increment a counter}.
   */
  AsyncSpanConsumer asyncSpanConsumer(CollectorSampler sampler, CollectorMetrics metrics);

  /**
   * Closes any network resources created implicitly by the component.
   *
   * <p>For example, if this created a connection, it would close it. If it was provided one, this
   * would close any sessions, but leave the connection open.
   */
  @Override void close();
}
