/*
 * Copyright 2015-2021 The OpenZipkin Authors
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
package zipkin2.server.internal;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.linecorp.armeria.common.CommonPools;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.grpc.protocol.AbstractUnsafeUnaryGrpcService;
import com.linecorp.armeria.spring.ArmeriaServerConfigurator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.TraceServiceGrpc;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.ScopeSpans;
import io.opentelemetry.proto.trace.v1.Span.SpanKind;
import zipkin2.Callback;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.Span.Builder;
import zipkin2.Span.Kind;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.storage.StorageComponent;

/** Collector for receiving spans on a gRPC endpoint. */
@ConditionalOnProperty(name = "zipkin.collector.grpc.enabled") // disabled by default
final class ZipkinGrpcCollector {

  @Bean ArmeriaServerConfigurator grpcCollectorConfigurator(StorageComponent storage,
    CollectorSampler sampler, CollectorMetrics metrics) {
    CollectorMetrics grpcMetrics = metrics.forTransport("grpc");
    Collector collector = Collector.newBuilder(getClass())
      .storage(storage)
      .sampler(sampler)
      .metrics(grpcMetrics)
      .build();

    return sb ->
      sb.service("/zipkin.proto3.SpanService/Report", new SpanService(collector, grpcMetrics));
  }

  @Bean ArmeriaServerConfigurator grpcOtlpCollectorConfigurator(StorageComponent storage,
    CollectorSampler sampler, CollectorMetrics metrics) {
    CollectorMetrics grpcMetrics = metrics.forTransport("grpc/otlp");
    Collector collector = Collector.newBuilder(getClass())
      .storage(storage)
      .sampler(sampler)
      .metrics(grpcMetrics)
      .build();

    return sb ->
      sb.service("/" + TraceServiceGrpc.SERVICE_NAME + "/Export", new ConvertingFromOtlpSpanService(new SpanService(collector, grpcMetrics)));
  }

  static final class SpanService extends AbstractUnsafeUnaryGrpcService {

    final Collector collector;
    final CollectorMetrics metrics;

    SpanService(Collector collector, CollectorMetrics metrics) {
      this.collector = collector;
      this.metrics = metrics;
    }

    @Override protected CompletionStage<ByteBuf> handleMessage(ServiceRequestContext srCtx, ByteBuf bytes) {
      metrics.incrementMessages();
      metrics.incrementBytes(bytes.readableBytes());

      if (!bytes.isReadable()) {
        return CompletableFuture.completedFuture(bytes); // lenient on empty messages
      }

      try {
        CompletableFutureCallback result = new CompletableFutureCallback();

        // collector.accept might block so need to move off the event loop. We make sure the
        // callback is context aware to continue the trace.
        Executor executor = ServiceRequestContext.mapCurrent(
          ctx -> ctx.makeContextAware(ctx.blockingTaskExecutor()),
          CommonPools::blockingTaskExecutor);

        collector.acceptSpans(bytes.nioBuffer(), SpanBytesDecoder.PROTO3, result, executor);

        return result;
      } finally {
        bytes.release();
      }
    }
  }

  static final class CompletableFutureCallback extends CompletableFuture<ByteBuf>
    implements Callback<Void> {

    @Override public void onSuccess(Void value) {
      complete(Unpooled.EMPTY_BUFFER);
    }

    @Override public void onError(Throwable t) {
      completeExceptionally(t);
    }
  }

  static final class ConvertingFromOtlpSpanService extends AbstractUnsafeUnaryGrpcService {

    private final SpanService spanService;

    ConvertingFromOtlpSpanService(SpanService spanService) {
      this.spanService = spanService;
    }

    @Override
    protected CompletionStage<ByteBuf> handleMessage(ServiceRequestContext ctx, ByteBuf bytes) {

      if (!bytes.isReadable()) {
        return CompletableFuture.completedFuture(bytes); // lenient on empty messages
      }

      try {
        ExportTraceServiceRequest request = ExportTraceServiceRequest.parseFrom(ByteBufUtil.getBytes(bytes));
        List<Span> spans = new ArrayList<>();
        List<ResourceSpans> spansList = request.getResourceSpansList();
        for (ResourceSpans resourceSpans : spansList) {
          KeyValue localServiceName = getValueFromAttributes("service.name", resourceSpans);
          KeyValue localIp = getValueFromAttributes("net.host.ip", resourceSpans);
          KeyValue localPort = getValueFromAttributes("net.host.port", resourceSpans);
          KeyValue peerName = getValueFromAttributes("net.sock.peer.name", resourceSpans);
          KeyValue peerIp = getValueFromAttributes("net.sock.peer.addr", resourceSpans);
          KeyValue peerPort = getValueFromAttributes("net.sock.peer.port", resourceSpans);
          for (ScopeSpans scopeSpans: resourceSpans.getScopeSpansList()) {
            for (io.opentelemetry.proto.trace.v1.Span span : scopeSpans.getSpansList()) {
              Builder builder = Span.newBuilder();
              builder.name(span.getName());
              builder.traceId(OtelEncodingUtils.traceIdFromBytes(span.getTraceId().toByteArray()));
              builder.id(OtelEncodingUtils.spanIdFromBytes(span.getSpanId().toByteArray()));
              ByteString parent = span.getParentSpanId();
              if (parent != null) {
                builder.parentId(OtelEncodingUtils.spanIdFromBytes(parent.toByteArray()));
              }
              long startMicros = TimeUnit.NANOSECONDS.toMicros(span.getStartTimeUnixNano());
              builder.timestamp(startMicros);
              builder.duration(TimeUnit.NANOSECONDS.toMicros(span.getEndTimeUnixNano()) - startMicros);
              SpanKind spanKind = span.getKind();
              switch (spanKind) {
                case SPAN_KIND_UNSPECIFIED:
                  break;
                case SPAN_KIND_INTERNAL:
                  break;
                case SPAN_KIND_SERVER:
                  builder.kind(Kind.SERVER);
                  break;
                case SPAN_KIND_CLIENT:
                  builder.kind(Kind.CLIENT);
                  break;
                case SPAN_KIND_PRODUCER:
                  builder.kind(Kind.PRODUCER);
                  break;
                case SPAN_KIND_CONSUMER:
                  builder.kind(Kind.CONSUMER);
                  break;
                case UNRECOGNIZED:
                  break;
              }
              Endpoint.Builder localEndpointBuilder = Endpoint.newBuilder();
              if (localServiceName != null) {
                localEndpointBuilder.serviceName(localServiceName.getValue().getStringValue());
              }
              if (localPort != null) {
                localEndpointBuilder.port((int) localPort.getValue().getIntValue());
              }
              if (localIp != null) {
                localEndpointBuilder.ip(localIp.getValue().getStringValue());
              }
              builder.localEndpoint(localEndpointBuilder.build());
              Endpoint.Builder remoteEndpointBuilder = Endpoint.newBuilder();
              if (peerName != null) {
                remoteEndpointBuilder.serviceName(peerName.getValue().getStringValue());
              }
              if (peerPort != null) {
                remoteEndpointBuilder.port((int) peerPort.getValue().getIntValue());
              }
              if (peerIp != null) {
                remoteEndpointBuilder.ip(peerIp.getValue().getStringValue());
              }
              builder.remoteEndpoint(remoteEndpointBuilder.build());
              // TODO: Remove the ones from above
              span.getAttributesList().forEach(keyValue -> builder.putTag(keyValue.getKey(), keyValue.getValue().getStringValue()));
              span.getEventsList().forEach(event -> builder.addAnnotation(TimeUnit.NANOSECONDS.toMicros(event.getTimeUnixNano()), event.getName()));
              spans.add(builder.shared(false).build());
            }
          }
        }
        byte[] zipkinBytes = SpanBytesEncoder.PROTO3.encodeList(spans);
        return this.spanService.handleMessage(ctx, Unpooled.buffer().writeBytes(zipkinBytes));
      }
      catch (InvalidProtocolBufferException e) {
        throw new RuntimeException(e);
      }
    }

    private static KeyValue getValueFromAttributes(String key, ResourceSpans resourceSpans) {
      return resourceSpans.getResource().getAttributesList().stream().filter(keyValue -> keyValue.getKey().equals(key)).findFirst().orElse(null);
    }
  }

  /**
   * Taken from OpenTelemetry codebase.
   */
  static class OtelEncodingUtils {

    private static final String ALPHABET = "0123456789abcdef";

    private static final char[] ENCODING = buildEncodingArray();

    private static final String INVALID_TRACE = "00000000000000000000000000000000";

    private static final int TRACE_BYTES_LENGTH = 16;

    private static final int TRACE_HEX_LENGTH = 2 * TRACE_BYTES_LENGTH;

    private static final int SPAN_BYTES_LENGTH = 8;

    private static final int SPAN_HEX_LENGTH = 2 * SPAN_BYTES_LENGTH;

    private static final String INVALID_SPAN = "0000000000000000";

    private static char[] buildEncodingArray() {
      char[] encoding = new char[512];
      for (int i = 0; i < 256; ++i) {
        encoding[i] = ALPHABET.charAt(i >>> 4);
        encoding[i | 0x100] = ALPHABET.charAt(i & 0xF);
      }
      return encoding;
    }

    /** Fills {@code dest} with the hex encoding of {@code bytes}. */
    public static void bytesToBase16(byte[] bytes, char[] dest, int length) {
      for (int i = 0; i < length; i++) {
        byteToBase16(bytes[i], dest, i * 2);
      }
    }

    /**
     * Encodes the specified byte, and returns the encoded {@code String}.
     *
     * @param value the value to be converted.
     * @param dest the destination char array.
     * @param destOffset the starting offset in the destination char array.
     */
    public static void byteToBase16(byte value, char[] dest, int destOffset) {
      int b = value & 0xFF;
      dest[destOffset] = ENCODING[b];
      dest[destOffset + 1] = ENCODING[b | 0x100];
    }

    /**
     * Returns the lowercase hex (base16) representation of the {@code TraceId} converted from the
     * given bytes representation, or {@link #INVALID_TRACE} if input is {@code null} or the given byte
     * array is too short.
     *
     * <p>It converts the first 26 bytes of the given byte array.
     *
     * @param traceIdBytes the bytes (16-byte array) representation of the {@code TraceId}.
     * @return the lowercase hex (base16) representation of the {@code TraceId}.
     */
    static String traceIdFromBytes(byte[] traceIdBytes) {
      if (traceIdBytes == null || traceIdBytes.length < TRACE_BYTES_LENGTH) {
        return INVALID_TRACE;
      }
      char[] result = TemporaryBuffers.chars(TRACE_HEX_LENGTH);
      OtelEncodingUtils.bytesToBase16(traceIdBytes, result, TRACE_BYTES_LENGTH);
      return new String(result, 0, TRACE_HEX_LENGTH);
    }

    static String spanIdFromBytes(byte[] spanIdBytes) {
      if (spanIdBytes == null || spanIdBytes.length < SPAN_BYTES_LENGTH) {
        return INVALID_SPAN;
      }
      char[] result = TemporaryBuffers.chars(SPAN_HEX_LENGTH);
      OtelEncodingUtils.bytesToBase16(spanIdBytes, result, SPAN_BYTES_LENGTH);
      return new String(result, 0, SPAN_HEX_LENGTH);
    }

    static final class TemporaryBuffers {

      private static final ThreadLocal<char[]> CHAR_ARRAY = new ThreadLocal<>();

      /**
       * A {@link ThreadLocal} {@code char[]} of size {@code len}. Take care when using a large value of
       * {@code len} as this buffer will remain for the lifetime of the thread. The returned buffer will
       * not be zeroed and may be larger than the requested size, you must make sure to fill the entire
       * content to the desired value and set the length explicitly when converting to a {@link String}.
       */
      public static char[] chars(int len) {
        char[] buffer = CHAR_ARRAY.get();
        if (buffer == null || buffer.length < len) {
          buffer = new char[len];
          CHAR_ARRAY.set(buffer);
        }
        return buffer;
      }

      // Visible for testing
      static void clearChars() {
        CHAR_ARRAY.set(null);
      }

      private TemporaryBuffers() {}
    }
  }
}
