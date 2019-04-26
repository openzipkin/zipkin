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
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.Consumes;
import com.linecorp.armeria.server.annotation.ConsumesJson;
import com.linecorp.armeria.server.annotation.ExceptionHandler;
import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;
import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.server.annotation.RequestConverter;
import com.linecorp.armeria.server.annotation.RequestConverterFunction;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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
@RequestConverter(UnzippingBytesRequestConverter.class)
@ExceptionHandler(BodyIsExceptionMessage.class)
public class ZipkinHttpCollector {
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
  public HttpResponse uploadSpans(byte[] serializedSpans) {
    return validateAndStoreSpans(SpanBytesDecoder.JSON_V2, serializedSpans);
  }

  @Post("/api/v2/spans")
  @ConsumesJson
  public HttpResponse uploadSpansJson(byte[] serializedSpans) {
    return validateAndStoreSpans(SpanBytesDecoder.JSON_V2, serializedSpans);
  }

  @Post("/api/v2/spans")
  @ConsumesProtobuf
  public HttpResponse uploadSpansProtobuf(byte[] serializedSpans) {
    return validateAndStoreSpans(SpanBytesDecoder.PROTO3, serializedSpans);
  }

  @Post("/api/v1/spans")
  public HttpResponse uploadSpansV1(byte[] serializedSpans) {
    return validateAndStoreSpans(SpanBytesDecoder.JSON_V1, serializedSpans);
  }

  @Post("/api/v1/spans")
  @ConsumesJson
  public HttpResponse uploadSpansV1Json(byte[] serializedSpans) {
    return validateAndStoreSpans(SpanBytesDecoder.JSON_V1, serializedSpans);
  }

  @Post("/api/v1/spans")
  @ConsumesThrift
  public HttpResponse uploadSpansV1Thrift(byte[] serializedSpans) {
    return validateAndStoreSpans(SpanBytesDecoder.THRIFT, serializedSpans);
  }

  /** This synchronously decodes the message so that users can see data errors. */
  HttpResponse validateAndStoreSpans(SpanBytesDecoder decoder, byte[] serializedSpans) {
    // logging already handled upstream in UnzippingBytesRequestConverter where request context exists
    if (serializedSpans.length == 0) return HttpResponse.of(HttpStatus.ACCEPTED);
    try {
      SpanBytesDecoderDetector.decoderForListMessage(serializedSpans);
    } catch (IllegalArgumentException e) {
      metrics.incrementMessagesDropped();
      return HttpResponse.of(
        BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8, "Expected a " + decoder + " encoded list\n");
    }

    SpanBytesDecoder unexpectedDecoder = testForUnexpectedFormat(decoder, serializedSpans);
    if (unexpectedDecoder != null) {
      metrics.incrementMessagesDropped();
      return HttpResponse.of(
        BAD_REQUEST, MediaType.PLAIN_TEXT_UTF_8,
        "Expected a " + decoder + " encoded list, but received: " + unexpectedDecoder + "\n");
    }

    CompletableCallback result = new CompletableCallback();
    List<Span> spans = new ArrayList<>();
    if (!decoder.decodeList(serializedSpans, spans)) {
      throw new IllegalArgumentException("Empty " + decoder.name() + " message");
    }
    collector.accept(spans, result);
    return HttpResponse.from(result);
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

  @Override public void onSuccess(Void value) {
    complete(HttpResponse.of(HttpStatus.ACCEPTED));
  }

  @Override public void onError(Throwable t) {
    completeExceptionally(t);
  }
}

final class UnzippingBytesRequestConverter implements RequestConverterFunction {
  static final Logger LOGGER = LogManager.getLogger();
  static final GzipStreamDecoderFactory GZIP_DECODER_FACTORY = new GzipStreamDecoderFactory();

  @Override public Object convertRequest(ServiceRequestContext ctx, AggregatedHttpMessage request,
    Class<?> expectedResultType) {
    ZipkinHttpCollector.metrics.incrementMessages();
    String encoding = request.headers().get(HttpHeaderNames.CONTENT_ENCODING);
    HttpData content = request.content();
    if (!content.isEmpty() && encoding != null && encoding.contains("gzip")) {
      content = GZIP_DECODER_FACTORY.newDecoder().decode(content);
      // The implementation of the armeria decoder is to return an empty body of failure
      if (content.isEmpty()) throw new IllegalArgumentException("Cannot gunzip spans");
    }

    if (content.isEmpty()) {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Empty POST body sent by {}->{}, {}->{}",
          HttpHeaderNames.USER_AGENT, request.headers().get(HttpHeaderNames.USER_AGENT),
          HttpHeaderNames.X_FORWARDED_FOR, request.headers().get(HttpHeaderNames.X_FORWARDED_FOR)
        );
      }
    }

    byte[] result = content.array();
    ZipkinHttpCollector.metrics.incrementBytes(result.length);
    return result;
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
  static SpanBytesDecoder testForUnexpectedFormat(BytesDecoder<Span> decoder, byte[] body) {
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

  static boolean contains(byte[] bytes, byte[] subsequence) {
    bytes:
    for (int i = 0; i < bytes.length - subsequence.length + 1; i++) {
      for (int j = 0; j < subsequence.length; j++) {
        if (bytes[i + j] != subsequence[j]) {
          continue bytes;
        }
      }
      return true;
    }
    return false;
  }
}
