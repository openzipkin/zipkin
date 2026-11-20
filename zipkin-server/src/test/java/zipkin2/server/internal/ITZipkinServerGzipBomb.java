/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.server.internal;

import com.linecorp.armeria.server.Server;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import zipkin.server.ZipkinServer;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.storage.InMemoryStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.server.internal.ITZipkinServer.url;

/**
 * Regression test for https://github.com/openzipkin/zipkin/issues/3834. A small gzip request body
 * that inflates past the collector's decompressed-size ceiling must be rejected before the full
 * body is allocated, rather than expanding without bound and exhausting the heap.
 */
@SpringBootTest(
  classes = ZipkinServer.class,
  webEnvironment = SpringBootTest.WebEnvironment.NONE, // RANDOM_PORT requires spring-web
  properties = {
    "server.port=0",
    "spring.config.name=zipkin-server",
    // A deliberately tiny ceiling so the test payload amplifies past it while staying fast.
    "zipkin.collector.http.max-decompressed-bytes=8192",
  }
)
public class ITZipkinServerGzipBomb {

  @Autowired InMemoryStorage storage;
  @Autowired Server server;

  OkHttpClient client = new OkHttpClient.Builder().build();

  @BeforeEach void init() {
    storage.clear();
  }

  /** A small compressed body that inflates well past the ceiling is rejected, not accepted. */
  @Test void gzipInflatingPastCeiling_isRejected() throws IOException {
    // A list of thousands of identical spans compresses to a tiny wire payload but inflates to
    // hundreds of kilobytes, far past the 8 KiB ceiling configured above.
    List<Span> many = new ArrayList<>();
    for (int i = 0; i < 5000; i++) many.add(CLIENT_SPAN);
    byte[] json = SpanBytesEncoder.JSON_V2.encodeList(many);
    byte[] gzipped = gzip(json);

    // Sanity: the exploit shape is a small wire payload that expands past the ceiling.
    assertThat(json.length).isGreaterThan(8192);
    assertThat(gzipped.length).isLessThan(json.length);

    Response response = postGzip(gzipped);

    assertThat(response.code()).isEqualTo(400);
    assertThat(response.body().string()).contains("decompressed size exceeds");
    // The oversized body was never accepted for storage.
    assertThat(storage.acceptedSpanCount()).isZero();
  }

  /** A normal gzip request that stays under the ceiling is still accepted. */
  @Test void gzipUnderCeiling_isAccepted() throws IOException {
    byte[] json = SpanBytesEncoder.JSON_V2.encodeList(List.of(CLIENT_SPAN));
    assertThat(json.length).isLessThan(8192);

    Response response = postGzip(gzip(json));

    assertThat(response.code()).isEqualTo(202);
  }

  Response postGzip(byte[] body) throws IOException {
    return client.newCall(new Request.Builder()
      .url(url(server, "/api/v2/spans"))
      .header("Content-Encoding", "gzip")
      .post(RequestBody.create(body, MediaType.get("application/json")))
      .build()).execute();
  }

  static byte[] gzip(byte[] bytes) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
      gzip.write(bytes);
    }
    return out.toByteArray();
  }
}
