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
package zipkin2.internal;

import com.squareup.wire.ProtoWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import okio.ByteString;
import org.assertj.core.data.MapEntry;
import org.junit.Test;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.internal.Proto3ZipkinFields.TagField;
import zipkin2.proto3.Annotation;
import zipkin2.proto3.Endpoint;
import zipkin2.proto3.ListOfSpans;
import zipkin2.proto3.Span;

import static java.util.Collections.singletonMap;
import static okio.ByteString.decodeHex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static zipkin2.internal.Proto3ZipkinFields.SPAN;
import static zipkin2.internal.Proto3ZipkinFields.SpanField.ANNOTATION;
import static zipkin2.internal.Proto3ZipkinFields.SpanField.LOCAL_ENDPOINT;
import static zipkin2.internal.Proto3ZipkinFields.SpanField.REMOTE_ENDPOINT;
import static zipkin2.internal.Proto3ZipkinFields.SpanField.TAG_KEY;

// Compares against Square Wire as it is easier than Google's protobuf tooling
public class Proto3CodecInteropTest {
  static final zipkin2.Endpoint ORDER = zipkin2.Endpoint.newBuilder()
    .serviceName("订单维护服务")
    .ip("2001:db8::c001")
    .build();

  static final zipkin2.Endpoint PROFILE = zipkin2.Endpoint.newBuilder()
    .serviceName("个人信息服务")
    .ip("192.168.99.101")
    .port(9000)
    .build();

  static final zipkin2.Span ZIPKIN_SPAN = zipkin2.Span.newBuilder()
    .traceId("4d1e00c0db9010db86154a4ba6e91385")
    .parentId("86154a4ba6e91385")
    .id("4d1e00c0db9010db")
    .kind(zipkin2.Span.Kind.SERVER)
    .name("个人信息查询")
    .timestamp(1472470996199000L)
    .duration(207000L)
    .localEndpoint(ORDER)
    .remoteEndpoint(PROFILE)
    .addAnnotation(1472470996199000L, "foo happened")
    .putTag("http.path", "/person/profile/query")
    .putTag("http.status_code", "403")
    .putTag("clnt/finagle.version", "6.45.0")
    .putTag("error", "此用户没有操作权限")
    .shared(true)
    .build();
  static final List<zipkin2.Span> ZIPKIN_SPANS = Arrays.asList(ZIPKIN_SPAN, ZIPKIN_SPAN);

  static final Span PROTO_SPAN = new Span.Builder()
    .trace_id(decodeHex(ZIPKIN_SPAN.traceId()))
    .parent_id(decodeHex(ZIPKIN_SPAN.parentId()))
    .id(decodeHex(ZIPKIN_SPAN.id()))
    .kind(Span.Kind.valueOf(ZIPKIN_SPAN.kind().name()))
    .name(ZIPKIN_SPAN.name())
    .timestamp(ZIPKIN_SPAN.timestampAsLong())
    .duration(ZIPKIN_SPAN.durationAsLong())
    .local_endpoint(new Endpoint.Builder()
      .service_name(ORDER.serviceName())
      .ipv6(ByteString.of(ORDER.ipv6Bytes())).build()
    )
    .remote_endpoint(new Endpoint.Builder()
      .service_name(PROFILE.serviceName())
      .ipv4(ByteString.of(PROFILE.ipv4Bytes()))
      .port(PROFILE.portAsInt()).build()
    )
    .annotations(Arrays.asList(new Annotation.Builder()
      .timestamp(ZIPKIN_SPAN.annotations().get(0).timestamp())
      .value(ZIPKIN_SPAN.annotations().get(0).value())
      .build()))
    .tags(ZIPKIN_SPAN.tags())
    .shared(true)
    .build();
  ListOfSpans PROTO_SPANS = new ListOfSpans.Builder()
    .spans(Arrays.asList(PROTO_SPAN, PROTO_SPAN)).build();

  @Test public void encodeIsCompatible() throws IOException {
    okio.Buffer buffer = new okio.Buffer();

    Span.ADAPTER.encodeWithTag(new ProtoWriter(buffer), 1, PROTO_SPAN);

    assertThat(SpanBytesEncoder.PROTO3.encode(ZIPKIN_SPAN))
      .containsExactly(buffer.readByteArray());
  }

  @Test public void decodeOneIsCompatible() {
    assertThat(SpanBytesDecoder.PROTO3.decodeOne(PROTO_SPANS.encode()))
      .isEqualTo(ZIPKIN_SPAN);
  }

  @Test public void decodeListIsCompatible() {
    assertThat(SpanBytesDecoder.PROTO3.decodeList(PROTO_SPANS.encode()))
      .containsExactly(ZIPKIN_SPAN, ZIPKIN_SPAN);
  }

  @Test public void encodeListIsCompatible_buff() {
    byte[] wireBytes = PROTO_SPANS.encode();
    byte[] zipkin_buff = new byte[10 + wireBytes.length];

    assertThat(SpanBytesEncoder.PROTO3.encodeList(ZIPKIN_SPANS, zipkin_buff, 5))
      .isEqualTo(wireBytes.length);

    assertThat(zipkin_buff)
      .startsWith(0, 0, 0, 0, 0)
      .containsSequence(wireBytes)
      .endsWith(0, 0, 0, 0, 0);
  }

  @Test public void encodeListIsCompatible() {
    byte[] wireBytes = PROTO_SPANS.encode();

    assertThat(SpanBytesEncoder.PROTO3.encodeList(ZIPKIN_SPANS))
      .containsExactly(wireBytes);
  }

  @Test public void span_sizeInBytes_matchesWire() {
    assertThat(SPAN.sizeInBytes(ZIPKIN_SPAN))
      .isEqualTo(Span.ADAPTER.encodedSizeWithTag(SPAN.fieldNumber, PROTO_SPAN));
  }

  @Test public void annotation_sizeInBytes_matchesWire() {
    zipkin2.Annotation zipkinAnnotation = ZIPKIN_SPAN.annotations().get(0);

    assertThat(ANNOTATION.sizeInBytes(zipkinAnnotation)).isEqualTo(
      Annotation.ADAPTER.encodedSizeWithTag(ANNOTATION.fieldNumber, PROTO_SPAN.annotations.get(0))
    );
  }

  @Test public void annotation_write_matchesWire() {
    zipkin2.Annotation zipkinAnnotation = ZIPKIN_SPAN.annotations().get(0);
    Span wireSpan = new Span.Builder().annotations(PROTO_SPAN.annotations).build();

    byte[] zipkinBytes = new byte[ANNOTATION.sizeInBytes(zipkinAnnotation)];
    ANNOTATION.write(WriteBuffer.wrap(zipkinBytes, 0), zipkinAnnotation);

    assertThat(zipkinBytes)
      .containsExactly(wireSpan.encode());
  }

  @Test public void annotation_read_matchesWireEncodingWithTag() {
    zipkin2.Annotation zipkinAnnotation = ZIPKIN_SPAN.annotations().get(0);
    Span wireSpan = new Span.Builder().annotations(PROTO_SPAN.annotations).build();

    ReadBuffer wireBytes = ReadBuffer.wrap(wireSpan.encode());
    assertThat(wireBytes.readVarint32())
      .isEqualTo(ANNOTATION.key);

    zipkin2.Span.Builder builder = zipkinSpanBuilder();
    ANNOTATION.readLengthPrefixAndValue(wireBytes, builder);
    assertThat(builder.build().annotations())
      .containsExactly(zipkinAnnotation);
  }

  @Test public void endpoint_sizeInBytes_matchesWireEncodingWithTag() {
    assertThat(LOCAL_ENDPOINT.sizeInBytes(ZIPKIN_SPAN.localEndpoint())).isEqualTo(
      Endpoint.ADAPTER.encodedSizeWithTag(LOCAL_ENDPOINT.fieldNumber, PROTO_SPAN.local_endpoint)
    );

    assertThat(REMOTE_ENDPOINT.sizeInBytes(ZIPKIN_SPAN.remoteEndpoint())).isEqualTo(
      Endpoint.ADAPTER.encodedSizeWithTag(REMOTE_ENDPOINT.fieldNumber, PROTO_SPAN.remote_endpoint)
    );
  }

  @Test public void localEndpoint_write_matchesWire() {
    byte[] zipkinBytes = new byte[LOCAL_ENDPOINT.sizeInBytes(ZIPKIN_SPAN.localEndpoint())];
    LOCAL_ENDPOINT.write(WriteBuffer.wrap(zipkinBytes, 0), ZIPKIN_SPAN.localEndpoint());
    Span wireSpan = new Span.Builder().local_endpoint(PROTO_SPAN.local_endpoint).build();

    assertThat(zipkinBytes)
      .containsExactly(wireSpan.encode());
  }

  @Test public void remoteEndpoint_write_matchesWire() {
    byte[] zipkinBytes = new byte[REMOTE_ENDPOINT.sizeInBytes(ZIPKIN_SPAN.remoteEndpoint())];
    REMOTE_ENDPOINT.write(WriteBuffer.wrap(zipkinBytes, 0), ZIPKIN_SPAN.remoteEndpoint());
    Span wireSpan = new Span.Builder().remote_endpoint(PROTO_SPAN.remote_endpoint).build();

    assertThat(zipkinBytes)
      .containsExactly(wireSpan.encode());
  }

  @Test public void tag_sizeInBytes_matchesWire() {
    MapEntry<String, String> entry = entry("clnt/finagle.version", "6.45.0");
    Span wireSpan = new Span.Builder().tags(singletonMap(entry.key, entry.value)).build();

    assertThat(new TagField(TAG_KEY).sizeInBytes(entry))
      .isEqualTo(Span.ADAPTER.encodedSize(wireSpan));
  }

  @Test public void writeTagField_matchesWire() {
    MapEntry<String, String> entry = entry("clnt/finagle.version", "6.45.0");
    TagField field = new TagField(TAG_KEY);
    byte[] zipkinBytes = new byte[field.sizeInBytes(entry)];
    field.write(WriteBuffer.wrap(zipkinBytes, 0), entry);

    Span oneField = new Span.Builder().tags(singletonMap(entry.key, entry.value)).build();
    assertThat(zipkinBytes)
      .containsExactly(oneField.encode());
  }

  @Test public void writeTagField_matchesWire_emptyValue() {
    MapEntry<String, String> entry = entry("error", "");
    TagField field = new TagField(TAG_KEY);
    byte[] zipkinBytes = new byte[field.sizeInBytes(entry)];
    field.write(WriteBuffer.wrap(zipkinBytes, 0), entry);

    Span oneField = new Span.Builder().tags(singletonMap(entry.key, entry.value)).build();
    assertThat(zipkinBytes)
      .containsExactly(oneField.encode());
  }

  static zipkin2.Span.Builder zipkinSpanBuilder() {
    return zipkin2.Span.newBuilder().traceId("a").id("a");
  }
}
