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
package zipkin2.internal;

import com.google.common.io.BaseEncoding;
import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.data.MapEntry;
import org.junit.Test;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.internal.Proto3Fields.Utf8Field;
import zipkin2.internal.Proto3ZipkinFields.EndpointField;
import zipkin2.internal.Proto3ZipkinFields.TagField;
import zipkin2.proto3.Annotation;
import zipkin2.proto3.Endpoint;
import zipkin2.proto3.ListOfSpans;
import zipkin2.proto3.Span;

import static com.google.protobuf.CodedOutputStream.computeMessageSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static zipkin2.internal.Proto3ZipkinFields.SPAN;
import static zipkin2.internal.Proto3ZipkinFields.SpanField.ANNOTATION;
import static zipkin2.internal.Proto3ZipkinFields.SpanField.LOCAL_ENDPOINT;
import static zipkin2.internal.Proto3ZipkinFields.SpanField.REMOTE_ENDPOINT;
import static zipkin2.internal.Proto3ZipkinFields.SpanField.TAG_KEY;

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

  static final Span PROTO_SPAN = Span.newBuilder()
    .setTraceId(decodeHex(ZIPKIN_SPAN.traceId()))
    .setParentId(decodeHex(ZIPKIN_SPAN.parentId()))
    .setId(decodeHex(ZIPKIN_SPAN.id()))
    .setKind(Span.Kind.valueOf(ZIPKIN_SPAN.kind().name()))
    .setName(ZIPKIN_SPAN.name())
    .setTimestamp(ZIPKIN_SPAN.timestampAsLong())
    .setDuration(ZIPKIN_SPAN.durationAsLong())
    .setLocalEndpoint(Endpoint.newBuilder()
      .setServiceName(ORDER.serviceName())
      .setIpv6(ByteString.copyFrom(ORDER.ipv6Bytes())).build()
    )
    .setRemoteEndpoint(Endpoint.newBuilder()
      .setServiceName(PROFILE.serviceName())
      .setIpv4(ByteString.copyFrom(PROFILE.ipv4Bytes()))
      .setPort(PROFILE.portAsInt()).build()
    )
    .addAnnotations(Annotation.newBuilder()
      .setTimestamp(ZIPKIN_SPAN.annotations().get(0).timestamp())
      .setValue(ZIPKIN_SPAN.annotations().get(0).value())
      .build())
    .putAllTags(ZIPKIN_SPAN.tags())
    .setShared(true)
    .build();
  ListOfSpans PROTO_SPANS = ListOfSpans.newBuilder()
    .addSpans(PROTO_SPAN)
    .addSpans(PROTO_SPAN).build();

  @Test public void encodeIsCompatible() throws Exception {
    byte[] googleBytes = new byte[computeMessageSize(1, PROTO_SPAN)];
    CodedOutputStream out = CodedOutputStream.newInstance(googleBytes);
    out.writeMessage(1, PROTO_SPAN);

    assertThat(SpanBytesEncoder.PROTO3.encode(ZIPKIN_SPAN))
      .containsExactly(googleBytes);
  }

  @Test public void decodeOneIsCompatible() {
    assertThat(SpanBytesDecoder.PROTO3.decodeOne(PROTO_SPANS.toByteArray()))
      .isEqualTo(ZIPKIN_SPAN);
  }

  @Test public void decodeListIsCompatible() {
    assertThat(SpanBytesDecoder.PROTO3.decodeList(PROTO_SPANS.toByteArray()))
      .containsExactly(ZIPKIN_SPAN, ZIPKIN_SPAN);
  }

  @Test public void encodeListIsCompatible_buff() throws Exception {
    byte[] googleBytes = new byte[PROTO_SPANS.getSerializedSize()];
    CodedOutputStream out = CodedOutputStream.newInstance(googleBytes);
    PROTO_SPANS.writeTo(out);

    byte[] zipkin_buff = new byte[10 + googleBytes.length];
    assertThat(SpanBytesEncoder.PROTO3.encodeList(ZIPKIN_SPANS, zipkin_buff, 5))
      .isEqualTo(googleBytes.length);

    assertThat(zipkin_buff)
      .startsWith(0, 0, 0, 0, 0)
      .containsSequence(googleBytes)
      .endsWith(0, 0, 0, 0, 0);
  }

  @Test public void encodeListIsCompatible() throws Exception {
    byte[] googleBytes = new byte[PROTO_SPANS.getSerializedSize()];
    CodedOutputStream out = CodedOutputStream.newInstance(googleBytes);
    PROTO_SPANS.writeTo(out);

    assertThat(SpanBytesEncoder.PROTO3.encodeList(ZIPKIN_SPANS))
      .containsExactly(googleBytes);
  }

  @Test public void span_sizeInBytes_matchesProto3() {
    assertThat(SPAN.sizeInBytes(ZIPKIN_SPAN))
      .isEqualTo(computeMessageSize(SPAN.fieldNumber, PROTO_SPAN));
  }

  @Test public void annotation_sizeInBytes_matchesProto3() {
    zipkin2.Annotation zipkinAnnotation = ZIPKIN_SPAN.annotations().get(0);

    assertThat(ANNOTATION.sizeInBytes(zipkinAnnotation))
      .isEqualTo(computeMessageSize(ANNOTATION.fieldNumber, PROTO_SPAN.getAnnotations(0)));
  }

  @Test public void annotation_write_matchesProto3() throws IOException {
    zipkin2.Annotation zipkinAnnotation = ZIPKIN_SPAN.annotations().get(0);
    Annotation protoAnnotation = PROTO_SPAN.getAnnotations(0);

    Buffer zipkinBytes = new Buffer(ANNOTATION.sizeInBytes(zipkinAnnotation));
    ANNOTATION.write(zipkinBytes, zipkinAnnotation);

    assertThat(zipkinBytes.toByteArray())
      .containsExactly(writeSpan(Span.newBuilder().addAnnotations(protoAnnotation).build()));
  }

  @Test public void annotation_read_matchesProto3() throws IOException {
    zipkin2.Annotation zipkinAnnotation = ZIPKIN_SPAN.annotations().get(0);
    Annotation protoAnnotation = PROTO_SPAN.getAnnotations(0);

    Buffer zipkinBytes =
      new Buffer(writeSpan(Span.newBuilder().addAnnotations(protoAnnotation).build()), 0);
    assertThat(zipkinBytes.readVarint32())
      .isEqualTo(ANNOTATION.key);

    zipkin2.Span.Builder builder = zipkinSpanBuilder();
    ANNOTATION.readLengthPrefixAndValue(zipkinBytes, builder);
    assertThat(builder.build().annotations())
      .containsExactly(zipkinAnnotation);
  }

  @Test public void endpoint_sizeInBytes_matchesProto3() {
    assertThat(LOCAL_ENDPOINT.sizeInBytes(ZIPKIN_SPAN.localEndpoint()))
      .isEqualTo(computeMessageSize(LOCAL_ENDPOINT.fieldNumber, PROTO_SPAN.getLocalEndpoint()));

    assertThat(REMOTE_ENDPOINT.sizeInBytes(ZIPKIN_SPAN.remoteEndpoint()))
      .isEqualTo(computeMessageSize(REMOTE_ENDPOINT.fieldNumber, PROTO_SPAN.getRemoteEndpoint()));
  }

  @Test public void localEndpoint_write_matchesProto3() throws IOException {
    Buffer zipkinBytes = new Buffer(LOCAL_ENDPOINT.sizeInBytes(ZIPKIN_SPAN.localEndpoint()));
    LOCAL_ENDPOINT.write(zipkinBytes, ZIPKIN_SPAN.localEndpoint());

    assertThat(zipkinBytes.toByteArray())
      .containsExactly(
        writeSpan(Span.newBuilder().setLocalEndpoint(PROTO_SPAN.getLocalEndpoint()).build()));
  }

  @Test public void remoteEndpoint_write_matchesProto3() throws IOException {
    Buffer zipkinBytes = new Buffer(REMOTE_ENDPOINT.sizeInBytes(ZIPKIN_SPAN.remoteEndpoint()));
    REMOTE_ENDPOINT.write(zipkinBytes, ZIPKIN_SPAN.remoteEndpoint());

    assertThat(zipkinBytes.toByteArray())
      .containsExactly(
        writeSpan(Span.newBuilder().setRemoteEndpoint(PROTO_SPAN.getRemoteEndpoint()).build()));
  }

  @Test public void utf8_sizeInBytes_matchesProto3() {
    assertThat(new Utf8Field(EndpointField.SERVICE_NAME_KEY).sizeInBytes(ORDER.serviceName()))
      .isEqualTo(CodedOutputStream.computeStringSize(1, ORDER.serviceName()));
  }

  @Test public void tag_sizeInBytes_matchesProto3() {
    MapEntry<String, String> entry = entry("clnt/finagle.version", "6.45.0");
    assertThat(new TagField(TAG_KEY).sizeInBytes(entry))
      .isEqualTo(Span.newBuilder().putTags(entry.key, entry.value).build().getSerializedSize());
  }

  @Test public void writeTagField_matchesProto3() throws IOException {
    MapEntry<String, String> entry = entry("clnt/finagle.version", "6.45.0");
    TagField field = new TagField(TAG_KEY);
    Buffer zipkinBytes = new Buffer(field.sizeInBytes(entry));
    field.write(zipkinBytes, entry);

    Span oneField = Span.newBuilder().putTags(entry.key, entry.value).build();
    byte[] googleBytes = new byte[oneField.getSerializedSize()];
    CodedOutputStream out = CodedOutputStream.newInstance(googleBytes);
    oneField.writeTo(out);

    assertThat(zipkinBytes.toByteArray())
      .containsExactly(googleBytes);
  }

  @Test public void writeTagField_matchesProto3_emptyValue() throws IOException {
    MapEntry<String, String> entry = entry("error", "");
    TagField field = new TagField(TAG_KEY);
    Buffer zipkinBytes = new Buffer(field.sizeInBytes(entry));
    field.write(zipkinBytes, entry);

    Span oneField = Span.newBuilder().putTags(entry.key, entry.value).build();
    byte[] googleBytes = new byte[oneField.getSerializedSize()];
    CodedOutputStream out = CodedOutputStream.newInstance(googleBytes);
    oneField.writeTo(out);

    assertThat(zipkinBytes.toByteArray())
      .containsExactly(googleBytes);
  }

  static ByteString decodeHex(String s) {
    return ByteString.copyFrom(BaseEncoding.base16().lowerCase().decode(s));
  }

  static byte[] writeSpan(Span span) throws IOException {
    byte[] googleBytes = new byte[span.getSerializedSize()];
    span.writeTo(CodedOutputStream.newInstance(googleBytes));
    return googleBytes;
  }

  static zipkin2.Span.Builder zipkinSpanBuilder() {
    return zipkin2.Span.newBuilder().traceId("a").id("a");
  }
}
