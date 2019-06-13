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
package zipkin2.codec;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import zipkin2.Annotation;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.internal.ReadBuffer;

public final class JacksonSpanDecoder {

  static final JsonFactory JSON_FACTORY = new JsonFactory();

  public static List<Span> decodeList(byte[] spans) {
    try {
      return decodeList(JSON_FACTORY.createParser(spans));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static List<Span> decodeList(ByteBuffer spans) {
    try {
      return decodeList(JSON_FACTORY.createParser(ReadBuffer.wrapUnsafe(spans)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Span decodeOne(byte[] span) {
    try {
      return decodeOne(JSON_FACTORY.createParser(span));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Span decodeOne(ByteBuffer span) {
    try {
      return decodeOne(JSON_FACTORY.createParser(ReadBuffer.wrapUnsafe(span)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static List<Span> decodeList(JsonParser jsonParser) {
    List<Span> out = new ArrayList<>();

    try {
      if (jsonParser.nextToken() != JsonToken.START_ARRAY) {
        throw new IOException("Not a valid JSON array, start token: " + jsonParser.currentToken());
      }

      while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
        out.add(parseSpan(jsonParser));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    return out;
  }

  static Span decodeOne(JsonParser jsonParser) {
    try {
      if (!jsonParser.hasCurrentToken()) {
        jsonParser.nextToken();
      }
      return parseSpan(jsonParser);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Span parseSpan(JsonParser jsonParser) throws IOException {
    if (!jsonParser.isExpectedStartObjectToken()) {
      throw new IOException("Not a valid JSON object, start token: " +
        jsonParser.currentToken());
    }

    Span.Builder result = Span.newBuilder();

    while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
      String fieldName = jsonParser.currentName();
      JsonToken value = jsonParser.nextToken();
      if (value == JsonToken.VALUE_NULL) {
        continue;
      }
      switch (fieldName) {
        case "traceId":
          result.traceId(jsonParser.getValueAsString());
          break;
        case "parentId":
          result.parentId(jsonParser.getValueAsString());
          break;
        case "id":
          result.id(jsonParser.getValueAsString());
          break;
        case "kind":
          result.kind(Span.Kind.valueOf(jsonParser.getValueAsString()));
          break;
        case "name":
          result.name(jsonParser.getValueAsString());
          break;
        case "timestamp":
          result.timestamp(jsonParser.getValueAsLong());
          break;
        case "duration":
          result.duration(jsonParser.getValueAsLong());
          break;
        case "localEndpoint":
          result.localEndpoint(parseEndpoint(jsonParser));
          break;
        case "remoteEndpoint":
          result.remoteEndpoint(parseEndpoint(jsonParser));
          break;
        case "annotations":
          if (!jsonParser.isExpectedStartArrayToken()) {
            throw new IOException("Invalid span, expecting annotations array start, got: " +
              value);
          }
          while (jsonParser.nextToken() != JsonToken.END_ARRAY) {
            Annotation a = parseAnnotation(jsonParser);
            result.addAnnotation(a.timestamp(), a.value());
          }
          break;
        case "tags":
          if (value != JsonToken.START_OBJECT) {
            throw new IOException("Invalid span, expecting tags object, got: " + value);
          }
          while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
            result.putTag(jsonParser.currentName(), jsonParser.nextTextValue());
          }
          break;
        case "debug":
          result.debug(jsonParser.getBooleanValue());
          break;
        case "shared":
          result.shared(jsonParser.getBooleanValue());
          break;
        default:
          jsonParser.skipChildren();
      }
    }

    return result.build();
  }

  static Endpoint parseEndpoint(JsonParser jsonParser) throws IOException {
    if (!jsonParser.isExpectedStartObjectToken()) {
      throw new IOException("Not a valid JSON object, start token: " +
        jsonParser.currentToken());
    }

    String serviceName = null, ipv4 = null, ipv6 = null;
    int port = 0;

    while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
      String fieldName = jsonParser.currentName();
      JsonToken value = jsonParser.nextToken();
      if (value == JsonToken.VALUE_NULL) {
        continue;
      }

      switch (fieldName) {
        case "serviceName":
          serviceName = jsonParser.getValueAsString();
          break;
        case "ipv4":
          ipv4 = jsonParser.getValueAsString();
          break;
        case "ipv6":
          ipv6 = jsonParser.getValueAsString();
          break;
        case "port":
          port = jsonParser.getValueAsInt();
          break;
        default:
          jsonParser.skipChildren();
      }
    }

    if (serviceName == null && ipv4 == null && ipv6 == null && port == 0) return null;
    return Endpoint.newBuilder()
      .serviceName(serviceName)
      .ip(ipv4)
      .ip(ipv6)
      .port(port)
      .build();
  }

  static Annotation parseAnnotation(JsonParser jsonParser) throws IOException {
    if (!jsonParser.isExpectedStartObjectToken()) {
      throw new IOException("Not a valid JSON object, start token: " +
        jsonParser.currentToken());
    }

    long timestamp = 0;
    String value = null;

    while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
      String fieldName = jsonParser.currentName();

      switch (fieldName) {
        case "timestamp":
          timestamp = jsonParser.getValueAsLong();
          break;
        case "value":
          value = jsonParser.getValueAsString();
          break;
        default:
          jsonParser.skipChildren();
      }
    }

    if (timestamp == 0 || value == null) {
      throw new IllegalStateException("Incomplete annotation at " + jsonParser.currentToken());
    }
    return Annotation.create(timestamp, value);
  }
}
