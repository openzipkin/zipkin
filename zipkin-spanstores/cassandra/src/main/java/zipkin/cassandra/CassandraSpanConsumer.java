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
package zipkin.cassandra;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import org.twitter.zipkin.storage.cassandra.Repository;
import zipkin.Span;
import zipkin.SpanConsumer;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.internal.ThriftCodec;

import static zipkin.cassandra.CassandraUtil.annotationKeys;

// Extracted for readability
final class CassandraSpanConsumer implements SpanConsumer {

  /**
   * Internal flag that allows you read-your-writes consistency during tests.
   *
   * <p>This is internal as collection endpoints are usually in different threads or not in the same
   * process as query ones. Special-casing this allows tests to pass without changing {@link
   * SpanConsumer#accept}.
   *
   * <p>Why not just change {@link SpanConsumer#accept} now? {@link SpanConsumer#accept} may indeed
   * need to change, but when that occurs, we'd want to choose something that is widely supportable,
   * and serving a specific use case. That api might not be a future, for example. Future is
   * difficult, for example, properly supporting and testing cancel. Further, there are other async
   * models such as callbacks that could be more supportable. Regardless, this work is best delayed
   * until there's a worthwhile use-case vs up-fronting only due to tests, and prematurely choosing
   * Future results.
   */
  static boolean BLOCK_ON_FUTURES;

  static final ThriftCodec THRIFT_CODEC = new ThriftCodec();

  private final Repository repository;
  private final int spanTtl;
  private final int indexTtl;

  CassandraSpanConsumer(Repository repository, int spanTtl, int indexTtl) {
    this.repository = repository;
    this.spanTtl = spanTtl;
    this.indexTtl = indexTtl;
  }

  /**
   * <p>Storing spans result in asynchronous operations to the backend repository. This
   * implementation neither blocks on nor checks these futures, as the api doesn't require it.
   */
  @Override
  public void accept(List<Span> spans) {
    List<ListenableFuture<?>> futures = new LinkedList<>();
    for (Span span : spans) {
      span = ApplyTimestampAndDuration.apply(span);
      futures.add(repository.storeSpan(
          span.traceId,
          span.timestamp != null ? span.timestamp : 0L,
          String.format("%d_%d_%d",
              span.id,
              span.annotations.hashCode(),
              span.binaryAnnotations.hashCode()),
          ByteBuffer.wrap(THRIFT_CODEC.writeSpan(span)),
          spanTtl
      ));

      for (String serviceName : span.serviceNames()) {
        // SpanStore.getServiceNames
        futures.add(repository.storeServiceName(serviceName, indexTtl));
        if (!span.name.isEmpty()) {
          // SpanStore.getSpanNames
          futures.add(repository.storeSpanName(serviceName, span.name, indexTtl));
        }

        if (span.timestamp != null) {
          // QueryRequest.serviceName
          futures.add(repository.storeTraceIdByServiceName(serviceName, span.timestamp,
              span.traceId, indexTtl));

          // QueryRequest.spanName
          if (!span.name.isEmpty()) {
            futures.add(repository.storeTraceIdBySpanName(
                serviceName, span.name, span.timestamp, span.traceId, indexTtl));
          }

          // QueryRequest.min/maxDuration
          if (span.duration != null) {
            // Contract for Repository.storeTraceIdByDuration is to store the span twice, once with
            // the span name and another with empty string.
            futures.add(repository.storeTraceIdByDuration(
                serviceName, span.name, span.timestamp, span.duration, span.traceId, indexTtl));
            if (!span.name.isEmpty()) { // If span.name == "", this would be redundant
              repository.storeTraceIdByDuration(
                  serviceName, "", span.timestamp, span.duration, span.traceId, indexTtl);
            }
          }
        }
      }
      // QueryRequest.annotations/binaryAnnotations
      if (span.timestamp != null) {
        for (ByteBuffer annotation : annotationKeys(span)) {
          futures.add(repository.storeTraceIdByAnnotation(annotation, span.timestamp, span.traceId,
              indexTtl));
        }
      }
    }
    if (BLOCK_ON_FUTURES) Futures.getUnchecked(Futures.allAsList(futures));
  }
}
