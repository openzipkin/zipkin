package zipkin2.server.grpc;

import static com.google.protobuf.CodedOutputStream.computeMessageSize;

import com.google.common.collect.Lists;
import com.google.protobuf.CodedOutputStream;
import io.grpc.stub.StreamObserver;
import zipkin2.Callback;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorComponent;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.proto3.PublishSpansResponse;
import zipkin2.proto3.Span;
import zipkin2.proto3.SpanServiceGrpc;
import zipkin2.storage.StorageComponent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class GrpcCollector extends CollectorComponent {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder extends CollectorComponent.Builder {
    Collector.Builder delegate = Collector.newBuilder(GrpcCollector.class);
    CollectorMetrics metrics = CollectorMetrics.NOOP_METRICS;
    GrpcCollectorType type = GrpcCollectorType.ARMERIA;

    public int port = 9412;
    private StorageComponent storage;
    private CollectorSampler sampler;

    public Builder() {
    }

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

    public Builder port(int port) {
      if (port < 0)
        throw new NullPointerException("port < 0");
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

  @Override
  public abstract CollectorComponent start();

  public static final class SpanService extends SpanServiceGrpc.SpanServiceImplBase {

    private final CollectorMetrics metrics;
    private final Collector collector;

    public SpanService(CollectorMetrics metrics, Collector collector) {
      this.metrics = metrics;
      this.collector = collector;
    }

    @Override
    public StreamObserver<Span> publishSpans(final StreamObserver<PublishSpansResponse> responseObserver) {
      return new SpanService.SpanStreamObserver(responseObserver);
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
}
