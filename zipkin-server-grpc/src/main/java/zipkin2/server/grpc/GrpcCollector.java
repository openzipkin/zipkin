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
import static io.grpc.MethodDescriptor.generateFullMethodName;

import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.Empty;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.unsafe.grpc.GrpcUnsafeBufferUtil;
import io.grpc.BindableService;
import io.grpc.KnownLength;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.internal.GrpcUtil;
import io.grpc.stub.ServerCalls;
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
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
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
      return this.type.newCollector(this, new SpanServiceUnary(collectorMetrics, collector));
    }
  }

  static class SpanServiceUnary extends SpanServiceGrpc.SpanServiceImplBase {

    private final CollectorMetrics metrics;
    private final Collector collector;

    SpanServiceUnary(CollectorMetrics metrics, Collector collector) {
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

  // Everything below here is likely to go away in the final revision of this PR.

  /**
   * We need to use our custom {@link zipkin2.internal.Proto3Codec} to parse the message sent in by gRPC. In
   * order to avoid the overhead of taking the input buffer as a ByteString and copying it to a buffer, we
   * provide our own stub that lets us access the input stream.
   */
  static abstract class SpanServiceImplBase implements BindableService {

    static final String SERVICE_NAME = "zipkin2.proto3.SpanService";

    static final MethodDescriptor<byte[], byte[]> PUBLISH_METHOD =
      MethodDescriptor.newBuilder(
        DirectMarshaller.INSTANCE,
        DirectMarshaller.INSTANCE)
        .setFullMethodName(generateFullMethodName(SERVICE_NAME, "PublishSpans"))
        .setType(MethodDescriptor.MethodType.CLIENT_STREAMING)
        .build();

    abstract StreamObserver<byte[]> publishSpans(StreamObserver<byte[]> responseObserver);

    @Override
    public final ServerServiceDefinition bindService() {
      return ServerServiceDefinition.builder(SERVICE_NAME)
        .addMethod(PUBLISH_METHOD, ServerCalls.asyncClientStreamingCall(this::publishSpans))
        .build();
    }

  }

  /**
   * Provides access to the raw bytes as opposed to marshalling. If {@link zipkin2.internal.Proto3Codec} took
   * an {@link InputStream} instead of a byte array, then we could actually do better here. Instead of copying the
   * input stream into a buffer to deserialize, we could potentially do a zero copy deserialization on the underlying
   * input stream.
   */
  private static class DirectMarshaller implements MethodDescriptor.Marshaller<byte[]> {

    private static final ThreadLocal<Reference<byte[]>> bufs = new ThreadLocal<>();

    private static final InputStream EMPTY_INPUT_STREAM = new InputStream() {
      @Override
      public int read() {
        return -1;
      }
    };

    static final DirectMarshaller INSTANCE = new DirectMarshaller();

    /**
     * @return An empty input stream since we don't currently return a response.
     */
    @Override
    public InputStream stream(byte[] value) {
      return EMPTY_INPUT_STREAM;
    }

    @Override
    public byte[] parse(InputStream stream) {
      return toByteArray(stream);
    }

    /**
     * Uses code borrowed from https://github.com/grpc/grpc-java/blob/master/protobuf-lite/src/main/java/io/grpc/
     * protobuf/lite/ProtoLiteUtils.java#L174
     *
     * @param stream
     */
    private byte[] toByteArray(InputStream stream) {
      try {
        if (stream instanceof KnownLength) {
          int size = stream.available();
          if (size > 0 && size <= GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE) {
            Reference<byte[]> ref;
            // buf should not be used after this method has returned.
            byte[] buf;
            if ((ref = bufs.get()) == null || (buf = ref.get()) == null || buf.length < size) {
              buf = new byte[size];
              bufs.set(new WeakReference<>(buf));
            }

            int remaining = size;
            while (remaining > 0) {
              int position = size - remaining;
              int count = stream.read(buf, position, remaining);
              if (count == -1) {
                break;
              }
              remaining -= count;
            }

            if (remaining != 0) {
              int position = size - remaining;
              throw new RuntimeException("size inaccurate: " + size + " != " + position);
            }
            return buf;
          }
       }
      } catch (IOException e) {
        throw Status.INTERNAL.withDescription("Internal stream parsing error").withCause(e).asRuntimeException();
      }
      throw Status.INTERNAL.withDescription("Unknown stream type: " + stream.getClass().getName())
        .asRuntimeException();
    }
  }

  /**
   * Implementation of zipkin.proto3.SpanService
   */
  private static final class SpanService extends SpanServiceImplBase {

    private final CollectorMetrics metrics;
    private final Collector collector;

    SpanService(CollectorMetrics metrics, Collector collector) {
      this.metrics = metrics;
      this.collector = collector;
    }

    @Override
    public StreamObserver<byte[]> publishSpans(StreamObserver<byte[]> responseObserver) {
      return new SpanService.SpanStreamObserver(responseObserver);
    }

    private class SpanStreamObserver implements StreamObserver<byte[]> {
      private final StreamObserver<byte[]> responseObserver;
      private final List<byte[]> spans = Lists.newArrayList();

      SpanStreamObserver(StreamObserver<byte[]> responseObserver) {
        this.responseObserver = responseObserver;
      }

      @Override
      public void onNext(byte[] bytes) {
        metrics.incrementMessages();
        spans.add(bytes);
      }

      @Override
      public void onError(Throwable throwable) {
        responseObserver.onError(throwable);
      }

      @Override
      public void onCompleted() {
        List<zipkin2.Span> zipkinSpans = new ArrayList<>(spans.size());
        for (byte[] span : spans) {
          zipkinSpans.add(SpanBytesDecoder.PROTO3.decodeOne(span));
        }
        collector.accept(zipkinSpans, new Callback<Void>() {
          @Override
          public void onSuccess(Void value) {
            // This is ok because we return a hard coded response in the marshaller
            responseObserver.onNext(null);
          }

          @Override
          public void onError(Throwable t) {
            responseObserver.onError(t);
          }
        });
      }
    }
  }
}
