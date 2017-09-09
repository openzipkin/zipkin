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
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import zipkin.internal.v2.Endpoint;
import zipkin.internal.v2.Span;
import zipkin.internal.v2.internal.JsonCodec;
import zipkin.internal.v2.internal.V2SpanWriter;

/** This is separate from {@link SpanBytesEncoder}, as it isn't needed for instrumentation */
public enum SpanBytesCodec implements BytesEncoder<Span>, BytesDecoder<Span> {
  /** Corresponds to the Zipkin v2 json format */
  JSON_V2 {
    @Override public Encoding encoding() {
      return Encoding.JSON;
    }

    @Override public int sizeInBytes(Span input) {
      return SpanBytesEncoder.JSON_V2.sizeInBytes(input);
    }

    @Override public byte[] encode(Span input) {
      return SpanBytesEncoder.JSON_V2.encode(input);
    }

    @Override public byte[] encodeList(List<Span> input) {
      return SpanBytesEncoder.JSON_V2.encodeList(input);
    }

    @Override public Span decode(byte[] span) { // ex decode span in dependencies job
      return JsonCodec.read(new SpanReader(), span);
    }

    @Override public List<Span> decodeList(byte[] spans) { // ex getTrace
      return JsonCodec.readList(new SpanReader(), spans);
    }

    @Override public byte[] encodeNestedList(List<List<Span>> traces) {
      return JsonCodec.writeNestedList(new V2SpanWriter(), traces);
    }

    @Override public List<List<Span>> decodeNestedList(byte[] traces) { // ex getTraces
      return JsonCodec.readList(new SpanListReader(), traces);
    }
  };

  /** Serializes a list of traces retrieved from storage into its binary form. */
  public abstract byte[] encodeNestedList(List<List<Span>> traces);

  /** throws {@linkplain IllegalArgumentException} if the traces couldn't be decoded */
  public abstract List<List<Span>> decodeNestedList(byte[] traces);

  static final class SpanReader implements JsonCodec.JsonReaderAdapter<Span> {
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
        } else if (reader.peek() == JsonToken.NULL) {
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
              throw new MalformedJsonException("Incomplete annotation at " + reader.getPath());
            }
            reader.endObject();
            builder.addAnnotation(timestamp, value);
          }
          reader.endArray();
        } else if (nextName.equals("tags")) {
          reader.beginObject();
          while (reader.hasNext()) {
            String key = reader.nextName();
            if (reader.peek() == JsonToken.NULL) {
              throw new MalformedJsonException("No value at " + reader.getPath());
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
  }

  static final JsonCodec.JsonReaderAdapter<Endpoint> ENDPOINT_READER = reader -> {
    Endpoint.Builder result = Endpoint.newBuilder();
    reader.beginObject();
    boolean readField = false;
    while (reader.hasNext()) {
      String nextName = reader.nextName();
      if (reader.peek() == JsonToken.NULL) {
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
    if (!readField) throw new MalformedJsonException("Empty endpoint at " + reader.getPath());
    return result.build();
  };


  static final class SpanListReader implements JsonCodec.JsonReaderAdapter<List<Span>> {
    SpanReader spanReader;

    @Override public List<Span> fromJson(JsonReader reader) throws IOException {
      reader.beginArray();
      if (!reader.hasNext()) {
        reader.endArray();
        return Collections.emptyList();
      }
      List<Span> result = new LinkedList<>(); // because we don't know how long it will be
      if (spanReader == null) spanReader = new SpanReader();
      while (reader.hasNext()) result.add(spanReader.fromJson(reader));
      reader.endArray();
      return result;
    }

    @Override public String toString() {
      return "List<Span>";
    }
  }
}
