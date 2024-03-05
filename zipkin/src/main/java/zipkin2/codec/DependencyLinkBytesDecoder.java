/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.codec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import zipkin2.DependencyLink;
import zipkin2.internal.JsonCodec;
import zipkin2.internal.JsonCodec.JsonReader;
import zipkin2.internal.JsonCodec.JsonReaderAdapter;
import zipkin2.internal.Nullable;
import zipkin2.internal.ReadBuffer;

public enum DependencyLinkBytesDecoder implements BytesDecoder<DependencyLink> {
  JSON_V1 {
    @Override public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override public boolean decode(byte[] link, Collection<DependencyLink> out) {
      return JsonCodec.read(READER, ReadBuffer.wrap(link), out);
    }

    @Override @Nullable public DependencyLink decodeOne(byte[] link) {
      return JsonCodec.readOne(READER, ReadBuffer.wrap(link));
    }

    @Override public boolean decodeList(byte[] links, Collection<DependencyLink> out) {
      return JsonCodec.readList(READER, ReadBuffer.wrap(links), out);
    }

    @Override public List<DependencyLink> decodeList(byte[] links) {
      List<DependencyLink> out = new ArrayList<>();
      decodeList(links, out);
      return out;
    }
  };

  static final JsonReaderAdapter<DependencyLink> READER = new JsonReaderAdapter<DependencyLink>() {
    @Override public DependencyLink fromJson(JsonReader reader) throws IOException {
      DependencyLink.Builder result = DependencyLink.newBuilder();
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (nextName.equals("parent")) {
          result.parent(reader.nextString());
        } else if (nextName.equals("child")) {
          result.child(reader.nextString());
        } else if (nextName.equals("callCount")) {
          result.callCount(reader.nextLong());
        } else if (nextName.equals("errorCount")) {
          result.errorCount(reader.nextLong());
        } else {
          reader.skipValue();
        }
      }
      reader.endObject();
      return result.build();
    }

    @Override public String toString() {
      return "DependencyLink";
    }
  };
}
