/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.codec;

import java.util.List;
import zipkin2.DependencyLink;
import zipkin2.internal.JsonCodec;
import zipkin2.internal.WriteBuffer;
import zipkin2.internal.WriteBuffer.Writer;

import static zipkin2.internal.JsonEscaper.jsonEscape;
import static zipkin2.internal.JsonEscaper.jsonEscapedSizeInBytes;
import static zipkin2.internal.WriteBuffer.asciiSizeInBytes;

public enum DependencyLinkBytesEncoder implements BytesEncoder<DependencyLink> {
  JSON_V1 {
    @Override public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override public int sizeInBytes(DependencyLink input) {
      return WRITER.sizeInBytes(input);
    }

    @Override public byte[] encode(DependencyLink link) {
      return JsonCodec.write(WRITER, link);
    }

    @Override public byte[] encodeList(List<DependencyLink> links) {
      return JsonCodec.writeList(WRITER, links);
    }
  };

  static final Writer<DependencyLink> WRITER = new Writer<DependencyLink>() {
    @Override public int sizeInBytes(DependencyLink value) {
      int sizeInBytes = 37; // {"parent":"","child":"","callCount":}
      sizeInBytes += jsonEscapedSizeInBytes(value.parent());
      sizeInBytes += jsonEscapedSizeInBytes(value.child());
      sizeInBytes += asciiSizeInBytes(value.callCount());
      if (value.errorCount() > 0) {
        sizeInBytes += 14; // ,"errorCount":
        sizeInBytes += asciiSizeInBytes(value.errorCount());
      }
      return sizeInBytes;
    }

    @Override public void write(DependencyLink value, WriteBuffer b) {
      b.writeAscii("{\"parent\":\"");
      b.writeUtf8(jsonEscape(value.parent()));
      b.writeAscii("\",\"child\":\"");
      b.writeUtf8(jsonEscape(value.child()));
      b.writeAscii("\",\"callCount\":");
      b.writeAscii(value.callCount());
      if (value.errorCount() > 0) {
        b.writeAscii(",\"errorCount\":");
        b.writeAscii(value.errorCount());
      }
      b.writeByte('}');
    }

    @Override public String toString() {
      return "DependencyLink";
    }
  };
}
