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
package zipkin2.server.internal;

import com.linecorp.armeria.client.encoding.GzipStreamDecoderFactory;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.server.annotation.Consumes;
import com.linecorp.armeria.server.annotation.ConsumesJson;
import com.linecorp.armeria.server.annotation.Header;
import com.linecorp.armeria.server.annotation.Post;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import zipkin2.Callback;
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.collector.Collector;
import zipkin2.collector.CollectorMetrics;
import zipkin2.collector.CollectorSampler;
import zipkin2.storage.StorageComponent;

@ConditionalOnProperty(name = "zipkin.collector.http.enabled", matchIfMissing = true)
public class ZipkinHttpCollector {
  static final GzipStreamDecoderFactory GZIP_DECODER_FACTORY = new GzipStreamDecoderFactory();

  final CollectorMetrics metrics;
  final Collector collector;

  @Autowired ZipkinHttpCollector(StorageComponent storage, CollectorSampler sampler,
    CollectorMetrics metrics) {
    this.metrics = metrics.forTransport("http");
    this.collector = Collector.newBuilder(getClass())
      .storage(storage).sampler(sampler).metrics(this.metrics).build();
  }

  @Post("/api/v2/spans")
  public HttpResponse uploadSpans(@Nullable @Header("content-encoding") String encoding,
    HttpData req) {
    return validateAndStoreSpans(encoding, SpanBytesDecoder.JSON_V2, req);
  }

  @Post("/api/v2/spans")
  @ConsumesJson
  public HttpResponse uploadSpansJson(@Nullable @Header("content-encoding") String encoding,
    HttpData req) {
    return validateAndStoreSpans(encoding, SpanBytesDecoder.JSON_V2, req);
  }

  @Post("/api/v2/spans")
  @ConsumesProtobuf
  public HttpResponse uploadSpansProtobuf(@Nullable @Header("content-encoding") String encoding,
    HttpData req) {
    return validateAndStoreSpans(encoding, SpanBytesDecoder.PROTO3, req);
  }

  @Post("/api/v1/spans")
  public HttpResponse uploadSpansV1(@Nullable @Header("content-encoding") String encoding,
    HttpData req) {
    return validateAndStoreSpans(encoding, SpanBytesDecoder.JSON_V1, req);
  }

  @Post("/api/v1/spans")
  @ConsumesJson
  public HttpResponse uploadSpansV1Json(@Nullable @Header("content-encoding") String encoding,
    HttpData req) {
    return validateAndStoreSpans(encoding, SpanBytesDecoder.JSON_V1, req);
  }

  @Post("/api/v1/spans")
  @ConsumesThrift
  public HttpResponse uploadSpansV1Thrift(@Nullable @Header("content-encoding") String encoding,
    HttpData req) {
    return validateAndStoreSpans(encoding, SpanBytesDecoder.THRIFT, req);
  }

  HttpResponse validateAndStoreSpans(String encoding, SpanBytesDecoder decoder,
    HttpData req) {
    CompletableFuture<HttpResponse> result = new CompletableFuture<>();
    metrics.incrementMessages();
    if (encoding != null && encoding.contains("gzip")) {
      try {
        req = GZIP_DECODER_FACTORY.newDecoder().decode(req);
      } catch (RuntimeException e) {
        metrics.incrementMessagesDropped();
        return HttpResponse.ofFailure(e);
      }
    }
    byte[] serializedSpans = req.array();
    metrics.incrementBytes(serializedSpans.length);
    List<Span> spans = new ArrayList<>();
    try {
      if (decoder.decodeList(serializedSpans, spans)) {
        return HttpResponse.of(HttpStatus.BAD_REQUEST, MediaType.ANY_TEXT_TYPE,
          "Could not decode spans");
      }
    } catch (RuntimeException e) {
      return HttpResponse.ofFailure(e);
    }
    collector.accept(spans, new Callback<Void>() {
      @Override public void onSuccess(Void value) {
        result.complete(HttpResponse.of(HttpStatus.ACCEPTED));
      }

      @Override public void onError(Throwable t) {
        result.completeExceptionally(t);
      }
    });
    return HttpResponse.from(result);
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE, ElementType.METHOD})
  @Consumes("application/x-thrift") @interface ConsumesThrift {
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE, ElementType.METHOD})
  @Consumes("application/x-protobuf") @interface ConsumesProtobuf {
  }
}
