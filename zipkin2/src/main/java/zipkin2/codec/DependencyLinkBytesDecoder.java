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
package zipkin2.codec;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import zipkin2.DependencyLink;
import zipkin2.internal.JsonCodec;
import zipkin2.internal.JsonCodec.JsonReader;
import zipkin2.internal.Nullable;

public enum DependencyLinkBytesDecoder implements BytesDecoder<DependencyLink> {
  JSON_V1 {
    @Override public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override public boolean decode(byte[] link, Collection<DependencyLink> out) {
      return JsonCodec.read(READER, link, out);
    }

    @Override @Nullable public DependencyLink decodeOne(byte[] link) {
      return JsonCodec.readOne(READER, link);
    }

    @Override public boolean decodeList(byte[] links, Collection<DependencyLink> out) {
      return JsonCodec.readList(READER, links, out);
    }  /** Visible for testing. This returns the first link parsed from the serialized object or null */

    /** Convenience method for {@link #decode(byte[], Collection)} */
    @Override public List<DependencyLink> decodeList(byte[] links) {
      return JsonCodec.readList(READER, links);
    }
  };

  static final JsonCodec.JsonReaderAdapter<DependencyLink>
    READER = new JsonCodec.JsonReaderAdapter<DependencyLink>() {
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
