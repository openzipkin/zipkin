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

import com.datastax.driver.core.Cluster;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import org.twitter.zipkin.storage.cassandra.Repository;
import zipkin.Codec;
import zipkin.Span;
import zipkin.internal.ApplyTimestampAndDuration;
import zipkin.spanstore.guava.GuavaSpanConsumer;

import static com.google.common.util.concurrent.Futures.transform;
import static zipkin.cassandra.CassandraUtil.annotationKeys;

/**
 * <p>Temporarily exposed until we make a storage component
 *
 * <p>See https://github.com/openzipkin/zipkin-java/issues/135
 */
public final class CassandraSpanConsumer implements GuavaSpanConsumer, AutoCloseable {
  private static final Function<Object, Void> TO_VOID = Functions.<Void>constant(null);

  private final Repository repository;
  private final int spanTtl;
  private final int indexTtl;

  public CassandraSpanConsumer(Cluster cluster, CassandraConfig config) {
    this.repository = new Repository(config.keyspace, cluster, config.ensureSchema);
    this.spanTtl = config.spanTtl;
    this.indexTtl = config.indexTtl;
  }

  @Override
  public ListenableFuture<Void> accept(List<Span> spans) {
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
          ByteBuffer.wrap(Codec.THRIFT.writeSpan(span)),
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
    return transform(Futures.allAsList(futures), TO_VOID);
  }

  @Override
  public void close() {
    repository.close();
  }
}
