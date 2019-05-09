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
package zipkin2.elasticsearch.internal;

import okio.Buffer;
import org.junit.Test;
import zipkin2.Span;
import zipkin2.Span.Kind;
import zipkin2.codec.SpanBytesDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static zipkin2.TestObjects.CLIENT_SPAN;
import static zipkin2.TestObjects.FRONTEND;
import static zipkin2.TestObjects.TODAY;

public class BulkIndexWriterTest {
  Buffer buffer = new Buffer();

  @Test public void span_addsDocumentId() {
    String id = BulkIndexWriter.SPAN.writeDocument(CLIENT_SPAN, buffer);
    assertThat(id)
      .isEqualTo(CLIENT_SPAN.traceId() + "-" + buffer.readByteString().md5().hex());
  }

  @Test public void spanSearchDisabled_addsDocumentId() {
    String id = BulkIndexWriter.SPAN_SEARCH_DISABLED.writeDocument(CLIENT_SPAN, buffer);
    assertThat(id)
      .isEqualTo(CLIENT_SPAN.traceId() + "-" + buffer.readByteString().md5().hex());
  }

  @Test public void spanSearchFields_skipsWhenNoData() {
    Span span = Span.newBuilder()
      .traceId("20")
      .id("22")
      .parentId("21")
      .timestamp(0L)
      .localEndpoint(FRONTEND)
      .kind(Kind.CLIENT)
      .build();

    BulkIndexWriter.SPAN.writeDocument(span, buffer);

    assertThat(buffer.readUtf8()).startsWith("{\"traceId\":\"");
  }

  @Test public void spanSearchFields_addsTimestampFieldWhenNoTags() {
    Span span =
      Span.newBuilder()
        .traceId("20")
        .id("22")
        .name("")
        .parentId("21")
        .timestamp(1000L)
        .localEndpoint(FRONTEND)
        .kind(Kind.CLIENT)
        .build();

    BulkIndexWriter.SPAN.writeDocument(span, buffer);

    assertThat(buffer.readUtf8()).startsWith("{\"timestamp_millis\":1,\"traceId\":");
  }

  @Test public void spanSearchFields_addsQueryFieldForAnnotations() {
    Span span = Span.newBuilder()
      .traceId("20")
      .id("22")
      .name("")
      .parentId("21")
      .localEndpoint(FRONTEND)
      .addAnnotation(1L, "\"foo")
      .build();

    BulkIndexWriter.SPAN.writeDocument(span, buffer);

    assertThat(buffer.readUtf8()).startsWith("{\"_q\":[\"\\\"foo\"],\"traceId");
  }

  @Test public void spanSearchFields_addsQueryFieldForTags() {
    Span span = Span.newBuilder()
      .traceId("20")
      .id("22")
      .parentId("21")
      .localEndpoint(FRONTEND)
      .putTag("\"foo", "\"bar")
      .build();

    BulkIndexWriter.SPAN.writeDocument(span, buffer);

    assertThat(buffer.readUtf8()).startsWith("{\"_q\":[\"\\\"foo\",\"\\\"foo=\\\"bar\"],\"traceId");
  }

  @Test public void spanSearchFields_readableByNormalJsonCodec() {
    Span span =
      Span.newBuilder().traceId("20").id("20").name("get").timestamp(TODAY * 1000).build();

    BulkIndexWriter.SPAN.writeDocument(span, buffer);

    assertThat(SpanBytesDecoder.JSON_V2.decodeOne(buffer.readByteArray()))
      .isEqualTo(span); // ignores timestamp_millis field
  }

  @Test public void spanSearchDisabled_doesntAddQueryFields() {
    BulkIndexWriter.SPAN_SEARCH_DISABLED.writeDocument(CLIENT_SPAN, buffer);

    assertThat(buffer.readUtf8()).startsWith("{\"traceId\":\"");
  }
}
