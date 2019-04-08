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
package zipkin2.collector.grpc;

import com.google.common.io.ByteStreams;
import com.google.protobuf.Empty;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.grpc.GrpcSerializationFormats;
import com.linecorp.armeria.server.ServiceWithPathMappings;
import com.linecorp.armeria.server.grpc.GrpcServiceBuilder;
import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls.UnaryMethod;
import io.grpc.stub.StreamObserver;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import zipkin2.Callback;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorComponent;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.storage.StorageComponent;

import static io.grpc.stub.ServerCalls.asyncUnaryCall;

/** Collector for receiving spans as gRPC endpoint {@link #FULL_METHOD_NAME}. */
public final class GrpcCollector extends CollectorComponent {
  static final String SERVICE_NAME = "zipkin.proto3.SpanService";
  static final String FULL_METHOD_NAME = SERVICE_NAME + "/Report";
  static final MethodDescriptor<byte[], Empty> ACCEPT_SPANS_METHOD =
    MethodDescriptor.<byte[], Empty>newBuilder()
      .setType(MethodDescriptor.MethodType.UNARY)
      .setFullMethodName(FULL_METHOD_NAME)
      .setRequestMarshaller(ByteArrayMarshaller.INSTANCE)
      .setResponseMarshaller(ProtoUtils.marshaller(Empty.getDefaultInstance()))
      .setSchemaDescriptor(null
        /* TODO: this NPEs at startup https://github.com/line/armeria/issues/1705 */)
      .build();

  public static Builder newBuilder() {
    return new Builder();
  }

  /** Configuration including defaults to consume spans from a gRPC endpoint. */
  public static final class Builder extends CollectorComponent.Builder {
    Collector.Builder delegate = Collector.newBuilder(GrpcCollector.class);
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;

    StorageComponent storage;
    CollectorSampler sampler;

    @Override public Builder storage(StorageComponent storage) {
      this.storage = storage;
      delegate.storage(storage);
      return this;
    }

    @Override public Builder metrics(CollectorMetrics metrics) {
      delegate.metrics(metrics);
      return this;
    }

    @Override public Builder sampler(CollectorSampler sampler) {
      this.sampler = sampler;
      delegate.sampler(sampler);
      return this;
    }

    @Override public GrpcCollector build() {
      CollectorMetrics collectorMetrics = metrics.forTransport("grpc");
      Collector collector = Collector.newBuilder(getClass())
        .storage(storage)
        .sampler(sampler)
        .metrics(collectorMetrics)
        .build();
      return new GrpcCollector(collector, collectorMetrics);
    }

    Builder() {
    }
  }

  public ServiceWithPathMappings<HttpRequest, HttpResponse> grpcService() {
    return new GrpcServiceBuilder()
      .supportedSerializationFormats(GrpcSerializationFormats.values())
      .unsafeWrapRequestBuffers(true)
      .addService(new SpanService(this))
      .build();
  }

  final Collector delegate;
  final CollectorMetrics metrics;

  GrpcCollector(Collector delegate, CollectorMetrics metrics) {
    this.delegate = delegate;
    this.metrics = metrics;
  }

  @Override public CollectorComponent start() {
    return this;
  }

  static final class SpanService implements BindableService, UnaryMethod<byte[], Empty> {
    final Collector collector;
    final CollectorMetrics metrics;

    SpanService(GrpcCollector component) {
      collector = component.delegate;
      metrics = component.metrics;
    }

    @Override public final ServerServiceDefinition bindService() {
      return ServerServiceDefinition.builder(SERVICE_NAME)
        .addMethod(ACCEPT_SPANS_METHOD, asyncUnaryCall(this)).build();
    }

    @Override public void invoke(byte[] bytes, StreamObserver<Empty> observer) {
      metrics.incrementMessages();
      collector.acceptSpans(bytes, SpanBytesDecoder.PROTO3, new StreamObserverCallback(observer));
    }
  }

  static final class StreamObserverCallback implements Callback<Void> {
    final StreamObserver<Empty> delegate;

    StreamObserverCallback(StreamObserver<Empty> delegate) {
      this.delegate = delegate;
    }

    @Override public void onSuccess(Void value) {
      delegate.onNext(Empty.getDefaultInstance());
      delegate.onCompleted();
    }

    @Override public void onError(Throwable t) {
      delegate.onError(t);
    }
  }

  enum ByteArrayMarshaller implements MethodDescriptor.Marshaller<byte[]> {
    INSTANCE;

    @Override public InputStream stream(byte[] value) {
      return new ByteArrayInputStream(value);
    }

    @Override public byte[] parse(InputStream stream) {
      try {
        return ByteStreams.toByteArray(stream);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
