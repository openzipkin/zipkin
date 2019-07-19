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
package zipkin2.elasticsearch.internal;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import zipkin2.Annotation;
import zipkin2.DependencyLink;
import zipkin2.Endpoint;
import zipkin2.Span;

/**
 * JSON serialization utilities and parsing code.
 */
public final class JsonSerializers {

  // Visible for testing
  public static final JsonFactory JSON_FACTORY = new JsonFactory();

  public static JsonParser jsonParser(ByteBuffer buf) {
    try {
      JsonParser jsonParser = JSON_FACTORY.createParser(new ByteBufferBackedInputStream(buf));
      jsonParser.nextToken();
      return jsonParser;
    } catch (IOException e) {
      throw new AssertionError("Could not create JsonParser from a buffer.", e);
    }
  }

  public static JsonParser jsonParser(InputStream stream) {
    try {
      JsonParser jsonParser = JSON_FACTORY.createParser(stream);
      jsonParser.nextToken();
      return jsonParser;
    } catch (IOException e) {
      throw new AssertionError("Could not create JsonParser from a buffer.", e);
    }
  }

  public static JsonGenerator jsonGenerator(OutputStream stream) {
    try {
      return JSON_FACTORY.createGenerator(stream);
    } catch (IOException e) {
      throw new AssertionError("Could not create JSON generator for a memory stream.", e);
    }
  }

  public interface ObjectParser<T> {
    T parse(JsonParser jsonParser) throws IOException;
  }

  public static final ObjectParser<Span> SPAN_PARSER = new ObjectParser<Span>() {
    @Override public Span parse(JsonParser jsonParser) throws IOException {
      return parseSpan(jsonParser);
    }
  };

  static Span parseSpan(JsonParser parser) throws IOException {
    if (!parser.isExpectedStartObjectToken()) {
      throw new IOException("Not a valid JSON object, start token: " +
        parser.currentToken());
    }

    Span.Builder result = Span.newBuilder();

    JsonToken value;
    while ((value = parser.nextValue()) != JsonToken.END_OBJECT) {
      if (value == null) {
        throw new IOException("End of input while parsing object.");
      }
      if (value == JsonToken.VALUE_NULL) {
        continue;
      }
      switch (parser.currentName()) {
        case "traceId":
          result.traceId(parser.getText());
          break;
        case "parentId":
          result.parentId(parser.getText());
          break;
        case "id":
          result.id(parser.getText());
          break;
        case "kind":
          result.kind(Span.Kind.valueOf(parser.getText()));
          break;
        case "name":
          result.name(parser.getText());
          break;
        case "timestamp":
          result.timestamp(parser.getLongValue());
          break;
        case "duration":
          result.duration(parser.getLongValue());
          break;
        case "localEndpoint":
          result.localEndpoint(parseEndpoint(parser));
          break;
        case "remoteEndpoint":
          result.remoteEndpoint(parseEndpoint(parser));
          break;
        case "annotations":
          if (value != JsonToken.START_ARRAY) {
            throw new IOException("Invalid span, expecting annotations array start, got: " +
              value);
          }
          while (parser.nextToken() != JsonToken.END_ARRAY) {
            Annotation a = parseAnnotation(parser);
            result.addAnnotation(a.timestamp(), a.value());
          }
          break;
        case "tags":
          if (value != JsonToken.START_OBJECT) {
            throw new IOException("Invalid span, expecting tags object, got: " + value);
          }
          while (parser.nextValue() != JsonToken.END_OBJECT) {
            result.putTag(parser.currentName(), parser.getValueAsString());
          }
          break;
        case "debug":
          result.debug(parser.getBooleanValue());
          break;
        case "shared":
          result.shared(parser.getBooleanValue());
          break;
        default:
          // Skip
      }
    }

    return result.build();
  }

  static Endpoint parseEndpoint(JsonParser parser) throws IOException {
    if (!parser.isExpectedStartObjectToken()) {
      throw new IOException("Not a valid JSON object, start token: " +
        parser.currentToken());
    }

    String serviceName = null, ipv4 = null, ipv6 = null;
    int port = 0;

    while (parser.nextToken() != JsonToken.END_OBJECT) {
      JsonToken value = parser.nextValue();
      if (value == JsonToken.VALUE_NULL) {
        continue;
      }

      switch (parser.currentName()) {
        case "serviceName":
          serviceName = parser.getText();
          break;
        case "ipv4":
          ipv4 = parser.getText();
          break;
        case "ipv6":
          ipv6 = parser.getText();
          break;
        case "port":
          port = parser.getIntValue();
          break;
        default:
          // Skip
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

  static Annotation parseAnnotation(JsonParser parser) throws IOException {
    if (!parser.isExpectedStartObjectToken()) {
      throw new IOException("Not a valid JSON object, start token: " +
        parser.currentToken());
    }

    long timestamp = 0;
    String value = null;

    while (parser.nextValue() != JsonToken.END_OBJECT) {
      switch (parser.currentName()) {
        case "timestamp":
          timestamp = parser.getLongValue();
          break;
        case "value":
          value = parser.getValueAsString();
          break;
        default:
          // Skip
      }
    }

    if (timestamp == 0 || value == null) {
      throw new IllegalStateException("Incomplete annotation at " + parser.currentToken());
    }
    return Annotation.create(timestamp, value);
  }

  public static final ObjectParser<DependencyLink> DEPENDENCY_LINK_PARSER =
    new ObjectParser<DependencyLink>() {
      @Override public DependencyLink parse(JsonParser parser) throws IOException {
        if (!parser.isExpectedStartObjectToken()) {
          throw new IOException("Expected start of dependency link object but was "
            + parser.currentToken());
        }

        DependencyLink.Builder result = DependencyLink.newBuilder();
        JsonToken value;
        while ((value = parser.nextValue()) != JsonToken.END_OBJECT) {
          if (value == null) {
            throw new IOException("End of input while parsing object.");
          }
          switch (parser.currentName()) {
            case "parent":
              result.parent(parser.getText());
              break;
            case "child":
              result.child(parser.getText());
              break;
            case "callCount":
              result.callCount(parser.getLongValue());
              break;
            case "errorCount":
              result.errorCount(parser.getLongValue());
              break;
            default:
              // Skip
          }
        }
        return result.build();
      }
    };
}
