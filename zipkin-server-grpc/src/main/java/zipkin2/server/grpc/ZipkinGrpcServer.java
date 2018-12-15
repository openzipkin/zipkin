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

import static com.google.protobuf.CodedOutputStream.computeMessageSize;

import com.google.common.collect.Lists;
import com.google.protobuf.CodedOutputStream;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import zipkin2.Callback;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.proto3.PublishSpansResponse;
import zipkin2.proto3.Span;
import zipkin2.proto3.SpanServiceGrpc;
import zipkin2.storage.StorageComponent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ZipkinGrpcServer extends SpanServiceGrpc.SpanServiceImplBase {

  public static Builder builder() {
    return new Builder();
  }

  /** Configuration including defaults needed to consume spans from a Kafka topic. */
  public static final class Builder {
    private CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    private CollectorSampler sampler;
    private StorageComponent storage;
    public int port = 9412;

    public Builder storage(StorageComponent storage) {
      if (storage == null) throw new NullPointerException("storage == null");
      this.storage = storage;
      return this;
    }

    public Builder metrics(CollectorMetrics metrics) {
      if (metrics == null) throw new NullPointerException("metrics == null");
      this.metrics = metrics;
      return this;
    }

    public Builder sampler(CollectorSampler sampler) {
      if (sampler == null) throw new NullPointerException("sampler == null");
      this.sampler = sampler;
      return this;
    }

    public Builder port(int port) {
      if (port < 0) throw new NullPointerException("port < 0");
      this.port = port;
      return this;
    }

    public ZipkinGrpcServer build() {
      return new ZipkinGrpcServer(this);
    }
  }

  private final Server server;
  private final CollectorMetrics metrics;
  private final Collector collector;

  private ZipkinGrpcServer(Builder builder) {
    this.metrics = builder.metrics.forTransport("grpc");
    this.collector =
        Collector.newBuilder(getClass())
            .storage(builder.storage)
            .sampler(builder.sampler)
            .metrics(this.metrics)
            .build();
    server = ServerBuilder.forPort(builder.port).addService(this).build();
  }

  /**
   * Start serving requests.
   */
  public void start() throws IOException {
    server.start();
  }

  /**
   * Stop serving requests and shutdown resources.
   */
  public void stop() {
    server.shutdown();
  }

  @Override
  public StreamObserver<Span> publishSpans(final StreamObserver<PublishSpansResponse> responseObserver) {
    return new SpanStreamObserver(responseObserver);
  }

  private class SpanStreamObserver implements StreamObserver<Span> {
    private final StreamObserver<PublishSpansResponse> responseObserver;
    private final List<Span> spans = Lists.newArrayList();

    SpanStreamObserver(StreamObserver<PublishSpansResponse> responseObserver) {
      this.responseObserver = responseObserver;
    }

    @Override
    public void onNext(Span span) {
      metrics.incrementMessages();
      spans.add(span);
    }

    @Override
    public void onError(Throwable throwable) {
      responseObserver.onError(throwable);
    }

    @Override
    public void onCompleted() {
      // TODO This is really gross at the moment. There is certainly a way to do this zero-copy but may need
      // to dive deeper into GRPC to prevent the serialization of the stub. Worst case scenario is we end
      // up writing our own RPC server: https://grpc.io/blog/grpc-with-json
      List<zipkin2.Span> zipkinSpans = new ArrayList<>(spans.size());
      for (Span span : spans) {
        byte[] buff = new byte[computeMessageSize(1, span)];
        try {
          CodedOutputStream out = CodedOutputStream.newInstance(buff);
          out.writeMessage(1, span);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        zipkinSpans.add(SpanBytesDecoder.PROTO3.decodeOne(buff));
      }

      collector.accept(zipkinSpans, new Callback<Void>() {
        @Override
        public void onSuccess(Void value) {
          responseObserver.onNext(PublishSpansResponse.getDefaultInstance());
        }

        @Override
        public void onError(Throwable t) {
          responseObserver.onError(t);
        }
      });
    }
  }

}
