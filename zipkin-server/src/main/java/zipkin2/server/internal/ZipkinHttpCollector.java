/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zipkin2.server.internal;

import com.linecorp.armeria.client.encoding.GzipStreamDecoderFactory;
import com.linecorp.armeria.common.AggregatedHttpMessage;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.Consumes;
import com.linecorp.armeria.server.annotation.ConsumesJson;
import com.linecorp.armeria.server.annotation.ExceptionHandler;
import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;
import com.linecorp.armeria.server.annotation.Post;
import io.netty.buffer.ByteBufHolder;
import io.netty.util.ReferenceCountUtil;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.SpanBytesDecoderDetector;
import zipkin2.codec.BytesDecoder;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.storage.StorageComponent;

import static com.linecorp.armeria.common.HttpStatus.BAD_REQUEST;
import static com.linecorp.armeria.common.HttpStatus.INTERNAL_SERVER_ERROR;
import static zipkin2.server.internal.BodyIsExceptionMessage.testForUnexpectedFormat;

@ConditionalOnProperty(name = "zipkin.collector.http.enabled", matchIfMissing = true)
@ExceptionHandler(BodyIsExceptionMessage.class)
public class ZipkinHttpCollector {
  static final Logger LOGGER = LogManager.getLogger();
  static volatile CollectorMetrics metrics;
  final Collector collector;

  ZipkinHttpCollector(
    StorageComponent storage, CollectorSampler sampler, CollectorMetrics metrics) {
    metrics = metrics.forTransport("http");
    collector =
      Collector.newBuilder(getClass()).storage(storage).sampler(sampler).metrics(metrics).build();
    ZipkinHttpCollector.metrics = metrics; // converter instances aren't injected by Spring
  }

  @Post("/api/v2/spans")
  public HttpResponse uploadSpans(ServiceRequestContext ctx, HttpRequest req) {
    return validateAndStoreSpans(SpanBytesDecoder.JSON_V2, ctx, req);
  }

  @Post("/api/v2/spans")
  @ConsumesJson
  public HttpResponse uploadSpansJson(ServiceRequestContext ctx, HttpRequest req) {
    return validateAndStoreSpans(SpanBytesDecoder.JSON_V2, ctx, req);
  }

  @Post("/api/v2/spans")
  @ConsumesProtobuf
  public HttpResponse uploadSpansProtobuf(ServiceRequestContext ctx, HttpRequest req) {
    return validateAndStoreSpans(SpanBytesDecoder.PROTO3, ctx, req);
  }

  @Post("/api/v1/spans")
  public HttpResponse uploadSpansV1(ServiceRequestContext ctx, HttpRequest req) {
    return validateAndStoreSpans(SpanBytesDecoder.JSON_V1, ctx, req);
  }

  @Post("/api/v1/spans")
  @ConsumesJson
  public HttpResponse uploadSpansV1Json(ServiceRequestContext ctx, HttpRequest req) {
    return validateAndStoreSpans(SpanBytesDecoder.JSON_V1, ctx, req);
  }

  @Post("/api/v1/spans")
  @ConsumesThrift
  public HttpResponse uploadSpansV1Thrift(ServiceRequestContext ctx, HttpRequest req) {
    return validateAndStoreSpans(SpanBytesDecoder.THRIFT, ctx, req);
  }

  /** This synchronously decodes the message so that users can see data errors. */
  HttpResponse validateAndStoreSpans(SpanBytesDecoder decoder, ServiceRequestContext ctx, HttpRequest req) {
    CompletableCallback result = new CompletableCallback();

    req.aggregateWithPooledObjects(ctx.eventLoop(), ctx.alloc()).handle((msg, t) -> {
      if (t != null) {
        result.onError(t);
        return null;
      }

      try {
        final HttpData content;
        try {
          content = UnzippingBytesRequestConverter.convertRequest(ctx, msg);
        } catch (IllegalArgumentException e) {
          result.onError(e);
          return null;
        }

        // logging already handled upstream in UnzippingBytesRequestConverter where request context exists
        if (content.isEmpty()) {
          result.onSuccess(null);
          return null;
        }

        final ByteBuffer nioBuffer;
        if (content instanceof ByteBufHolder) {
          nioBuffer = ((ByteBufHolder) content).content().nioBuffer();
        } else {
          // Currently this will happen for gzip spans. Need to fix armeria's gzip decoder to allow
          // returning pooled buffers on request.
          nioBuffer = ByteBuffer.wrap(content.array(), content.offset(), content.length());
        }

        try {
          SpanBytesDecoderDetector.decoderForListMessage(nioBuffer);
        } catch (IllegalArgumentException e) {
          result.onError(new IllegalArgumentException("Expected a " + decoder + " encoded list\n"));
          return null;
        }

        SpanBytesDecoder unexpectedDecoder = testForUnexpectedFormat(decoder, nioBuffer);
        if (unexpectedDecoder != null) {
          result.onError(new IllegalArgumentException(
            "Expected a " + decoder + " encoded list, but received: " + unexpectedDecoder + "\n"));
          return null;
        }

        List<Span> spans = new ArrayList<>();
        if (!decoder.decodeList(nioBuffer, spans)) {
          result.onError(new IllegalArgumentException("Empty " + decoder.name() + " message"));
          return null;
        }
        // UnzippingBytesRequestConverter handles incrementing message and bytes
        collector.accept(spans, result);
      } finally {
        ReferenceCountUtil.release(msg.content());
      }

      return null;
    });

    return HttpResponse.from(result);
  }

  static void maybeLog(String prefix, ServiceRequestContext ctx, AggregatedHttpMessage request) {
    if (!LOGGER.isDebugEnabled()) return;
    LOGGER.debug("{} sent by clientAddress->{}, userAgent->{}",
      prefix, ctx.clientAddress(), request.headers().get(HttpHeaderNames.USER_AGENT)
    );
  }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Consumes("application/x-thrift") @interface ConsumesThrift {
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Consumes("application/x-protobuf") @interface ConsumesProtobuf {
}

final class CompletableCallback extends CompletableFuture<HttpResponse>
  implements Callback<Void> {

  static final ResponseHeaders ACCEPTED_RESPONSE = ResponseHeaders.of(HttpStatus.ACCEPTED);

  @Override public void onSuccess(Void value) {
    complete(HttpResponse.of(ACCEPTED_RESPONSE));
  }

  @Override public void onError(Throwable t) {
    completeExceptionally(t);
  }
}

final class UnzippingBytesRequestConverter {
  static final GzipStreamDecoderFactory GZIP_DECODER_FACTORY = new GzipStreamDecoderFactory();

  static HttpData convertRequest(ServiceRequestContext ctx, AggregatedHttpMessage request) {
    ZipkinHttpCollector.metrics.incrementMessages();
    String encoding = request.headers().get(HttpHeaderNames.CONTENT_ENCODING);
    HttpData content = request.content();
    if (!content.isEmpty() && encoding != null && encoding.contains("gzip")) {
      content = GZIP_DECODER_FACTORY.newDecoder().decode(content);
      // The implementation of the armeria decoder is to return an empty body on failure
      if (content.isEmpty()) {
        ZipkinHttpCollector.maybeLog("Malformed gzip body", ctx, request);
        throw new IllegalArgumentException("Cannot gunzip spans");
      }
    }

    if (content.isEmpty()) ZipkinHttpCollector.maybeLog("Empty POST body", ctx, request);
    if (content.length() == 2 && "[]".equals(content.toStringAscii())) {
      ZipkinHttpCollector.maybeLog("Empty JSON list POST body", ctx, request);
      ReferenceCountUtil.release(content);
      content = HttpData.EMPTY_DATA;
    }

    ZipkinHttpCollector.metrics.incrementBytes(content.length());
    return content;
  }
}

final class BodyIsExceptionMessage implements ExceptionHandlerFunction {

  @Override
  public HttpResponse handleException(RequestContext ctx, HttpRequest req, Throwable cause) {
    ZipkinHttpCollector.metrics.incrementMessagesDropped();
    if (cause instanceof IllegalArgumentException) {
      return HttpResponse.of(BAD_REQUEST, MediaType.ANY_TEXT_TYPE, cause.getMessage());
    } else {
      return HttpResponse.of(INTERNAL_SERVER_ERROR, MediaType.ANY_TEXT_TYPE, cause.getMessage());
    }
  }

  /**
   * Some formats clash on partial data. For example, a v1 and v2 span is identical if only the span
   * name is sent. This looks for unexpected data format.
   */
  static SpanBytesDecoder testForUnexpectedFormat(BytesDecoder<Span> decoder, ByteBuffer body) {
    if (decoder == SpanBytesDecoder.JSON_V2) {
      if (contains(body, BINARY_ANNOTATION_FIELD_SUFFIX)) {
        return SpanBytesDecoder.JSON_V1;
      }
    } else if (decoder == SpanBytesDecoder.JSON_V1) {
      if (contains(body, ENDPOINT_FIELD_SUFFIX) || contains(body, TAGS_FIELD)) {
        return SpanBytesDecoder.JSON_V2;
      }
    }
    return null;
  }

  static final byte[] BINARY_ANNOTATION_FIELD_SUFFIX =
    {'y', 'A', 'n', 'n', 'o', 't', 'a', 't', 'i', 'o', 'n', 's', '"'};
  // copy-pasted from SpanBytesDecoderDetector, to avoid making it public
  static final byte[] ENDPOINT_FIELD_SUFFIX = {'E', 'n', 'd', 'p', 'o', 'i', 'n', 't', '"'};
  static final byte[] TAGS_FIELD = {'"', 't', 'a', 'g', 's', '"'};

  static boolean contains(ByteBuffer bytes, byte[] subsequence) {
    bytes:
    for (int i = 0; i < bytes.remaining() - subsequence.length + 1; i++) {
      for (int j = 0; j < subsequence.length; j++) {
        if (bytes.get(bytes.position() + i + j) != subsequence[j]) {
          continue bytes;
        }
      }
      return true;
    }
    return false;
  }
}
