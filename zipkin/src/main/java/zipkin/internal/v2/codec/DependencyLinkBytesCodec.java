/**
 * Copyright 2015-2017 The OpenZipkin Authors
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
package zipkin.internal.v2.codec;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.util.List;
import zipkin.internal.v2.DependencyLink;
import zipkin.internal.v2.internal.Buffer;
import zipkin.internal.v2.internal.JsonCodec;
import zipkin.internal.v2.internal.JsonCodec.JsonReaderAdapter;

import static zipkin.internal.v2.internal.Buffer.asciiSizeInBytes;
import static zipkin.internal.v2.internal.JsonEscaper.jsonEscape;
import static zipkin.internal.v2.internal.JsonEscaper.jsonEscapedSizeInBytes;

public enum DependencyLinkBytesCodec
  implements BytesEncoder<DependencyLink>, BytesDecoder<DependencyLink> {
  JSON {
    @Override public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override public int sizeInBytes(DependencyLink input) {
      return WRITER.sizeInBytes(input);
    }

    @Override public byte[] encode(DependencyLink link) {
      return JsonCodec.write(WRITER, link);
    }

    @Override public DependencyLink decode(byte[] link) {
      return JsonCodec.read(READER, link);
    }

    @Override public byte[] encodeList(List<DependencyLink> links) {
      return JsonCodec.writeList(WRITER, links);
    }

    @Override public List<DependencyLink> decodeList(byte[] links) {
      return JsonCodec.readList(READER, links);
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

  static final Buffer.Writer<DependencyLink> WRITER = new Buffer.Writer<DependencyLink>() {
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

    @Override public void write(DependencyLink value, Buffer b) {
      b.writeAscii("{\"parent\":\"").writeUtf8(jsonEscape(value.parent()));
      b.writeAscii("\",\"child\":\"").writeUtf8(jsonEscape(value.child()));
      b.writeAscii("\",\"callCount\":").writeAscii(value.callCount());
      if (value.errorCount() > 0) {
        b.writeAscii(",\"errorCount\":").writeAscii(value.errorCount());
      }
      b.writeByte('}');
    }

    @Override public String toString() {
      return "DependencyLink";
    }
  };
}
