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
package zipkin2.internal;

import java.io.IOException;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.JsonCodec.JsonReader;
import zipkin2.internal.JsonCodec.JsonReaderAdapter;

public final class V2SpanReader implements JsonReaderAdapter<Span> {
  Span.Builder builder;

  @Override public Span fromJson(JsonReader reader) throws IOException {
    if (builder == null) {
      builder = Span.newBuilder();
    } else {
      builder.clear();
    }
    reader.beginObject();
    while (reader.hasNext()) {
      String nextName = reader.nextName();
      if (nextName.equals("traceId")) {
        builder.traceId(reader.nextString());
        continue;
      } else if (nextName.equals("id")) {
        builder.id(reader.nextString());
        continue;
      } else if (reader.peekNull()) {
        reader.skipValue();
        continue;
      }

      // read any optional fields
      if (nextName.equals("parentId")) {
        builder.parentId(reader.nextString());
      } else if (nextName.equals("kind")) {
        builder.kind(Span.Kind.valueOf(reader.nextString()));
      } else if (nextName.equals("name")) {
        builder.name(reader.nextString());
      } else if (nextName.equals("timestamp")) {
        builder.timestamp(reader.nextLong());
      } else if (nextName.equals("duration")) {
        builder.duration(reader.nextLong());
      } else if (nextName.equals("localEndpoint")) {
        builder.localEndpoint(ENDPOINT_READER.fromJson(reader));
      } else if (nextName.equals("remoteEndpoint")) {
        builder.remoteEndpoint(ENDPOINT_READER.fromJson(reader));
      } else if (nextName.equals("annotations")) {
        reader.beginArray();
        while (reader.hasNext()) {
          reader.beginObject();
          Long timestamp = null;
          String value = null;
          while (reader.hasNext()) {
            nextName = reader.nextName();
            if (nextName.equals("timestamp")) {
              timestamp = reader.nextLong();
            } else if (nextName.equals("value")) {
              value = reader.nextString();
            } else {
              reader.skipValue();
            }
          }
          if (timestamp == null || value == null) {
            throw new IllegalArgumentException("Incomplete annotation at " + reader.getPath());
          }
          reader.endObject();
          builder.addAnnotation(timestamp, value);
        }
        reader.endArray();
      } else if (nextName.equals("tags")) {
        reader.beginObject();
        while (reader.hasNext()) {
          String key = reader.nextName();
          if (reader.peekNull()) {
            throw new IllegalArgumentException("No value at " + reader.getPath());
          }
          builder.putTag(key, reader.nextString());
        }
        reader.endObject();
      } else if (nextName.equals("debug")) {
        if (reader.nextBoolean()) builder.debug(true);
      } else if (nextName.equals("shared")) {
        if (reader.nextBoolean()) builder.shared(true);
      } else {
        reader.skipValue();
      }
    }
    reader.endObject();
    return builder.build();
  }

  @Override public String toString() {
    return "Span";
  }

  static final JsonReaderAdapter<Endpoint> ENDPOINT_READER = new JsonReaderAdapter<Endpoint>() {
    @Override public Endpoint fromJson(JsonReader reader) throws IOException {
      Endpoint.Builder result = Endpoint.newBuilder();
      reader.beginObject();
      boolean readField = false;
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (reader.peekNull()) {
          reader.skipValue();
          continue;
        }
        if (nextName.equals("serviceName")) {
          result.serviceName(reader.nextString());
          readField = true;
        } else if (nextName.equals("ipv4") || nextName.equals("ipv6")) {
          result.parseIp(reader.nextString());
          readField = true;
        } else if (nextName.equals("port")) {
          result.port(reader.nextInt());
          readField = true;
        } else {
          reader.skipValue();
        }
      }
      reader.endObject();
      if (!readField) throw new IllegalArgumentException("Empty endpoint at " + reader.getPath());
      return result.build();
    }

    @Override public String toString() {
      return "Endpoint";
    }
  };
}
