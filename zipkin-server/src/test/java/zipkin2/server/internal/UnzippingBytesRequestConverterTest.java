/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpHeaderNames;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.server.ServiceRequestContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.collector.CollectorMetrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static zipkin2.server.internal.UnzippingBytesRequestConverter.MAX_MESSAGE_BYTES;

class UnzippingBytesRequestConverterTest {

  @BeforeEach void setUpMetrics() {
    // convertRequest increments metrics; unit tests do not construct ZipkinHttpCollector.
    ZipkinHttpCollector.metrics = CollectorMetrics.NOOP_METRICS;
  }

  @Test void gzipWithinLimit_isAccepted() throws Exception {
    byte[] plain = new byte[1024];
    for (int i = 0; i < plain.length; i++) plain[i] = (byte) ('a' + (i % 26));
    HttpData content = convertGzip(plain);
    try {
      assertThat(content.toInputStream().readAllBytes()).isEqualTo(plain);
    } finally {
      content.close();
    }
  }

  @Test void gzipBomb_exceedingDecompressedLimit_isRejected() throws Exception {
    // Highly compressible payload: small on the wire, larger than MAX_MESSAGE_BYTES inflated.
    byte[] plain = new byte[MAX_MESSAGE_BYTES + 1];
    byte[] gzipped = gzip(plain);
    assertThat(gzipped.length).isLessThan(MAX_MESSAGE_BYTES / 100);

    AggregatedHttpRequest request = aggregatedGzip(gzipped);
    ServiceRequestContext ctx = ServiceRequestContext.of(HttpRequest.of(request.headers()));

    assertThatThrownBy(() -> UnzippingBytesRequestConverter.convertRequest(ctx, request))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Gzip decompressed body exceeds limit of " + MAX_MESSAGE_BYTES);
  }

  @Test void plainBodyExceedingLimit_isRejected() {
    byte[] plain = new byte[MAX_MESSAGE_BYTES + 1];
    AggregatedHttpRequest request = AggregatedHttpRequest.of(
      RequestHeaders.of(HttpMethod.POST, "/api/v2/spans",
        HttpHeaderNames.CONTENT_TYPE, MediaType.JSON),
      HttpData.wrap(plain));
    ServiceRequestContext ctx = ServiceRequestContext.of(HttpRequest.of(request.headers()));

    assertThatThrownBy(() -> UnzippingBytesRequestConverter.convertRequest(ctx, request))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Request body exceeds limit of " + MAX_MESSAGE_BYTES);
  }

  static HttpData convertGzip(byte[] plain) throws IOException {
    AggregatedHttpRequest request = aggregatedGzip(gzip(plain));
    ServiceRequestContext ctx = ServiceRequestContext.of(HttpRequest.of(request.headers()));
    return UnzippingBytesRequestConverter.convertRequest(ctx, request);
  }

  static AggregatedHttpRequest aggregatedGzip(byte[] gzipped) {
    return AggregatedHttpRequest.of(
      RequestHeaders.of(HttpMethod.POST, "/api/v2/spans",
        HttpHeaderNames.CONTENT_ENCODING, "gzip",
        HttpHeaderNames.CONTENT_TYPE, MediaType.JSON),
      HttpData.wrap(gzipped));
  }

  static byte[] gzip(byte[] plain) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
      gzip.write(plain);
    }
    return baos.toByteArray();
  }
}
