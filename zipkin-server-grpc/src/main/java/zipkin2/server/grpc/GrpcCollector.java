/*
 * Copyright 2015-2018 The OpenZipkin Authors
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
package zipkin2.server.grpc;

import static com.google.protobuf.CodedOutputStream.computeBytesSize;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Empty;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.unsafe.grpc.GrpcUnsafeBufferUtil;
import io.grpc.stub.StreamObserver;
import zipkin2.Callback;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorComponent;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.proto3.internal.ListOfSpansInternal;
import zipkin2.proto3.internal.SpanServiceGrpc;
import zipkin2.storage.StorageComponent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Collector for receiving spans as a gRPC endpoint.
 */
public abstract class GrpcCollector extends CollectorComponent {

  public static Builder builder() {
    return new Builder();
  }

  /** Configuration including defaults to consume spans from a gRPC endpoint. */
  public static final class Builder extends CollectorComponent.Builder {
    Collector.Builder delegate = Collector.newBuilder(GrpcCollector.class);
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    GrpcCollectorType type = GrpcCollectorType.ARMERIA;

    public int port = 9412;
    private StorageComponent storage;
    private CollectorSampler sampler;

    private Builder() {
    }

    /**
     * @param type The server type to use
     * @return the builder
     */
    public Builder type(GrpcCollectorType type) {
      if (type == null) throw new NullPointerException("type == null");
      this.type = type;
      return this;
    }

    public Builder storage(StorageComponent storage) {
      this.storage = storage;
      delegate.storage(storage);
      return this;
    }

    public Builder metrics(CollectorMetrics metrics) {
      delegate.metrics(metrics);
      return this;
    }

    public Builder sampler(CollectorSampler sampler) {
      this.sampler = sampler;
      delegate.sampler(sampler);
      return this;
    }

    /**
     * @param port The port to start the gRPC server on. In the future, this may run on the default port as the
     *             main server
     * @return the builder
     */
    public Builder port(int port) {
      if (port < 0) throw new NullPointerException("port < 0");
      this.port = port;
      return this;
    }

    public GrpcCollector build() {
      CollectorMetrics collectorMetrics = metrics.forTransport("grpc");
      Collector collector = Collector.newBuilder(getClass())
        .storage(storage)
        .sampler(sampler)
        .metrics(collectorMetrics)
        .build();
      return this.type.newCollector(this, new SpanService(collectorMetrics, collector));
    }
  }

  static class SpanService extends SpanServiceGrpc.SpanServiceImplBase {

    private final CollectorMetrics metrics;
    private final Collector collector;

    SpanService(CollectorMetrics metrics, Collector collector) {
      this.metrics = metrics;
      this.collector = collector;
    }

    @Override
    public void putSpans(ListOfSpansInternal request, StreamObserver<Empty> responseObserver) {
      try {
        List<ByteString> spans = request.getSpansList();
        List<zipkin2.Span> zipkinSpans = new ArrayList<>(spans.size());
        for (ByteString span : spans) {
          metrics.incrementMessages();
          // TODO Add support for Proto3Codec to take in a ByteBuffer and use span.asReadonlyByteBuffer() instead
          // so we don't have to copy
          try {
            byte[] buff = new byte[computeBytesSize(1, span)];
            CodedOutputStream out = CodedOutputStream.newInstance(buff);
            out.writeBytes(1, span);
            zipkinSpans.add(SpanBytesDecoder.PROTO3.decodeOne(buff));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }

        }
        collector.accept(zipkinSpans, new Callback<Void>() {
          @Override
          public void onSuccess(Void value) {
            // This is ok because we return a hard coded response in the marshaller
            responseObserver.onNext(Empty.getDefaultInstance());
          }

          @Override
          public void onError(Throwable t) {
            responseObserver.onError(t);
          }
        });
      } finally {
        GrpcUnsafeBufferUtil.releaseBuffer(request, RequestContext.current());
      }
    }
  }

}
