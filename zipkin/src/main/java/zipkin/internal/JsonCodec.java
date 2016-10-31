/**
 * Copyright 2015-2016 The OpenZipkin Authors
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
package zipkin.internal;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet6Address;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.BinaryAnnotation.Type;
import zipkin.Codec;
import zipkin.DependencyLink;
import zipkin.Endpoint;
import zipkin.Span;

import static java.lang.Double.doubleToRawLongBits;
import static zipkin.internal.Buffer.asciiSizeInBytes;
import static zipkin.internal.Buffer.base64UrlSizeInBytes;
import static zipkin.internal.Buffer.ipv6SizeInBytes;
import static zipkin.internal.Buffer.jsonEscapedSizeInBytes;
import static zipkin.internal.Buffer.utf8SizeInBytes;
import static zipkin.internal.Util.UTF_8;
import static zipkin.internal.Util.assertionError;
import static zipkin.internal.Util.checkArgument;
import static zipkin.internal.Util.lowerHexToUnsignedLong;

/**
 * This explicitly constructs instances of model classes via manual parsing for a number of
 * reasons.
 *
 * <ul>
 *   <li>Eliminates the need to keep separate model classes for thrift vs json</li>
 *   <li>Avoids magic field initialization which, can miss constructor guards</li>
 *   <li>Allows us to safely re-use the json form in toString methods</li>
 *   <li>Encourages logic to be based on the thrift shape of objects</li>
 *   <li>Ensures the order and naming of the fields in json is stable</li>
 * </ul>
 *
 * <p> There is the up-front cost of creating this, and maintenance of this to consider. However,
 * this should be easy to justify as these objects don't change much at all.
 */
public final class JsonCodec implements Codec {
  static final long MAX_SAFE_INTEGER = 9007199254740991L;  // 53 bits
  static final String ENDPOINT_HEADER = ",\"endpoint\":";

  static final JsonAdapter<Endpoint> ENDPOINT_ADAPTER = new JsonAdapter<Endpoint>() {
    @Override
    public Endpoint fromJson(JsonReader reader) throws IOException {
      Endpoint.Builder result = Endpoint.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (nextName.equals("serviceName")) {
          result.serviceName(reader.nextString());
        } else if (nextName.equals("ipv4")) {
          String[] ipv4String = reader.nextString().split("\\.", 5);
          int ipv4 = 0;
          for (String part : ipv4String) {
            ipv4 = ipv4 << 8 | (Integer.parseInt(part) & 0xff);
          }
          result.ipv4(ipv4);
        } else if (nextName.equals("ipv6")) {
          String input = reader.nextString();
          // Shouldn't hit DNS, because it's an IP string literal.
          result.ipv6(Inet6Address.getByName(input).getAddress());
        } else if (nextName.equals("port")) {
          result.port(reader.nextInt());
        } else {
          reader.skipValue();
        }
      }
      reader.endObject();
      return result.build();
    }

    @Override public int sizeInBytes(Endpoint value) {
      int sizeInBytes = 0;
      sizeInBytes += asciiSizeInBytes("{\"serviceName\":\"");
      sizeInBytes += jsonEscapedSizeInBytes(value.serviceName) + 1; // for end quote
      if (value.ipv4 != 0) {
        sizeInBytes += asciiSizeInBytes(",\"ipv4\":\"");
        sizeInBytes += asciiSizeInBytes(value.ipv4 >> 24 & 0xff) + 1; // for dot
        sizeInBytes += asciiSizeInBytes(value.ipv4 >> 16 & 0xff) + 1; // for dot
        sizeInBytes += asciiSizeInBytes(value.ipv4 >> 8 & 0xff) + 1; // for dot
        sizeInBytes += asciiSizeInBytes(value.ipv4 & 0xff) + 1; // for end quote
      }
      if (value.port != null && value.port != 0) {
        sizeInBytes += asciiSizeInBytes(",\"port\":") + asciiSizeInBytes(value.port & 0xffff);
      }
      if (value.ipv6 != null) {
        sizeInBytes += asciiSizeInBytes(",\"ipv6\":\"") + ipv6SizeInBytes(value.ipv6) + 1;
      }
      return ++sizeInBytes;// end curly-brace
    }

    @Override public void write(Endpoint value, Buffer b) {
      b.writeAscii("{\"serviceName\":\"");
      b.writeJsonEscaped(value.serviceName).writeByte('"');
      if (value.ipv4 != 0) {
        b.writeAscii(",\"ipv4\":\"");
        b.writeAscii(value.ipv4 >> 24 & 0xff).writeByte('.');
        b.writeAscii(value.ipv4 >> 16 & 0xff).writeByte('.');
        b.writeAscii(value.ipv4 >> 8 & 0xff).writeByte('.');
        b.writeAscii(value.ipv4 & 0xff).writeByte('"');
      }
      if (value.port != null && value.port != 0) {
        b.writeAscii(",\"port\":").writeAscii(value.port & 0xffff);
      }
      if (value.ipv6 != null) {
        b.writeAscii(",\"ipv6\":\"").writeIpV6(value.ipv6).writeByte('"');
      }
      b.writeByte('}');
    }
  };

  static final JsonAdapter<Annotation> ANNOTATION_ADAPTER = new JsonAdapter<Annotation>() {
    @Override
    public Annotation fromJson(JsonReader reader) throws IOException {
      Annotation.Builder result = Annotation.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (nextName.equals("timestamp")) {
          result.timestamp(reader.nextLong());
        } else if (nextName.equals("value")) {
          result.value(reader.nextString());
        } else if (nextName.equals("endpoint") && reader.peek() != JsonToken.NULL) {
          result.endpoint(ENDPOINT_ADAPTER.fromJson(reader));
        } else {
          reader.skipValue();
        }
      }
      reader.endObject();
      return result.build();
    }

    @Override public int sizeInBytes(Annotation value) {
      int sizeInBytes = 0;
      sizeInBytes += asciiSizeInBytes("{\"timestamp\":") + asciiSizeInBytes(value.timestamp);
      sizeInBytes += asciiSizeInBytes(",\"value\":\"") + jsonEscapedSizeInBytes(value.value) + 1;
      if (value.endpoint != null) {
        sizeInBytes += ENDPOINT_HEADER.length() + ENDPOINT_ADAPTER.sizeInBytes(value.endpoint);
      }
      return ++sizeInBytes;// end curly-brace
    }

    @Override public void write(Annotation value, Buffer b) {
      b.writeAscii("{\"timestamp\":").writeAscii(value.timestamp);
      b.writeAscii(",\"value\":\"").writeJsonEscaped(value.value).writeByte('"');
      if (value.endpoint != null) {
        b.writeAscii(ENDPOINT_HEADER);
        ENDPOINT_ADAPTER.write(value.endpoint, b);
      }
      b.writeByte('}');
    }
  };

  static final JsonAdapter<BinaryAnnotation> BINARY_ANNOTATION_ADAPTER = new JsonAdapter<BinaryAnnotation>() {
    @Override
    public BinaryAnnotation fromJson(JsonReader reader) throws IOException {
      BinaryAnnotation.Builder result = BinaryAnnotation.builder();
      String number = null;
      String string = null;
      Type type = Type.STRING;
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (nextName.equals("key")) {
          result.key(reader.nextString());
        } else if (nextName.equals("value")) {
          switch (reader.peek()) {
            case BOOLEAN:
              type = Type.BOOL;
              result.value(reader.nextBoolean() ? new byte[] {1} : new byte[] {0});
              break;
            case STRING:
              string = reader.nextString();
              break;
            case NUMBER:
              number = reader.nextString();
              break;
            default:
              throw new MalformedJsonException(
                  "Expected value to be a boolean, string or number but was " + reader.peek()
                      + " at path " + reader.getPath());
          }
        } else if (nextName.equals("type")) {
          type = Type.valueOf(reader.nextString());
        } else if (nextName.equals("endpoint") && reader.peek() != JsonToken.NULL) {
          result.endpoint(ENDPOINT_ADAPTER.fromJson(reader));
        } else {
          reader.skipValue();
        }
      }
      reader.endObject();
      result.type(type);
      switch (type) {
        case BOOL:
          return result.build();
        case STRING:
          return result.value(string.getBytes(UTF_8)).build();
        case BYTES:
          return result.value(Base64.decode(string)).build();
        default:
          break;
      }
      final byte[] value;
      if (type == Type.I16) {
        short v = Short.parseShort(number);
        value = ByteBuffer.allocate(2).putShort(0, v).array();
      } else if (type == Type.I32) {
        int v = Integer.parseInt(number);
        value = ByteBuffer.allocate(4).putInt(0, v).array();
      } else if (type == Type.I64 || type == Type.DOUBLE) {
        if (number == null) number = string;
        long v = type == Type.I64
            ? Long.parseLong(number)
            : doubleToRawLongBits(Double.parseDouble(number));
        value = ByteBuffer.allocate(8).putLong(0, v).array();
      } else {
        throw new AssertionError("BinaryAnnotationType " + type + " was added, but not handled");
      }
      return result.value(value).build();
    }

    @Override public int sizeInBytes(BinaryAnnotation value) {
      int sizeInBytes = 0;
      sizeInBytes += asciiSizeInBytes("{\"key\":\"") + jsonEscapedSizeInBytes(value.key);
      sizeInBytes += asciiSizeInBytes("\",\"value\":");
      switch (value.type) {
        case BOOL:
          sizeInBytes += asciiSizeInBytes(value.value[0] == 1 ? "true" : "false");
          break;
        case STRING:
          sizeInBytes += jsonEscapedSizeInBytes(value.value) + 2; //for quotes
          break;
        case BYTES:
          sizeInBytes += base64UrlSizeInBytes(value.value) +2; //for quotes
          break;
        case I16:
          sizeInBytes += asciiSizeInBytes(ByteBuffer.wrap(value.value).getShort());
          break;
        case I32:
          sizeInBytes += asciiSizeInBytes(ByteBuffer.wrap(value.value).getInt());
          break;
        case I64:
          long number = ByteBuffer.wrap(value.value).getLong();
          sizeInBytes += asciiSizeInBytes(number);
          if (number > MAX_SAFE_INTEGER) sizeInBytes += 2; //for quotes
          break;
        case DOUBLE:
          double wrapped = Double.longBitsToDouble(ByteBuffer.wrap(value.value).getLong());
          sizeInBytes += asciiSizeInBytes(Double.toString(wrapped));
          break;
        default:
      }
      if (value.type != BinaryAnnotation.Type.STRING && value.type != BinaryAnnotation.Type.BOOL) {
        sizeInBytes += asciiSizeInBytes(",\"type\":\"") + utf8SizeInBytes(value.type.name()) + 1;
      }
      if (value.endpoint != null) {
        sizeInBytes += ENDPOINT_HEADER.length() + ENDPOINT_ADAPTER.sizeInBytes(value.endpoint);
      }
      return ++sizeInBytes;// end curly-brace
    }

    @Override public void write(BinaryAnnotation value, Buffer b) {
      b.writeAscii("{\"key\":\"").writeJsonEscaped(value.key);
      b.writeAscii("\",\"value\":");
      switch (value.type) {
        case BOOL:
          b.writeAscii(value.value[0] == 1 ? "true" : "false");
          break;
        case STRING:
          b.writeByte('"').writeJsonEscaped(value.value).writeByte('"');
          break;
        case BYTES:
          b.writeByte('"').writeBase64Url(value.value).writeByte('"');
          break;
        case I16:
          b.writeAscii(ByteBuffer.wrap(value.value).getShort());
          break;
        case I32:
          b.writeAscii(ByteBuffer.wrap(value.value).getInt());
          break;
        case I64:
          long number = ByteBuffer.wrap(value.value).getLong();
          if (number > MAX_SAFE_INTEGER) b.writeByte('"');
          b.writeAscii(number);
          if (number > MAX_SAFE_INTEGER) b.writeByte('"');
          break;
        case DOUBLE:
          double wrapped = Double.longBitsToDouble(ByteBuffer.wrap(value.value).getLong());
          b.writeAscii(Double.toString(wrapped));
          break;
        default:
      }
      if (value.type != BinaryAnnotation.Type.STRING && value.type != BinaryAnnotation.Type.BOOL) {
        b.writeAscii(",\"type\":\"").writeAscii(value.type.name()).writeByte('"');
      }
      if (value.endpoint != null) {
        b.writeAscii(ENDPOINT_HEADER);
        ENDPOINT_ADAPTER.write(value.endpoint, b);
      }
      b.writeByte('}');
    }
  };

  static final JsonAdapter<Span> SPAN_ADAPTER = new JsonAdapter<Span>() {
    @Override
    public Span fromJson(JsonReader reader) throws IOException {
      Span.Builder result = Span.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (nextName.equals("traceId")) {
          String traceId = reader.nextString();
          if (traceId.length() == 32) {
            result.traceIdHigh(lowerHexToUnsignedLong(traceId, 0));
          }
          result.traceId(lowerHexToUnsignedLong(traceId));
        } else if (nextName.equals("name")) {
          result.name(reader.nextString());
        } else if (nextName.equals("id")) {
          result.id(lowerHexToUnsignedLong(reader.nextString()));
        } else if (nextName.equals("parentId") && reader.peek() != JsonToken.NULL) {
          result.parentId(lowerHexToUnsignedLong(reader.nextString()));
        } else if (nextName.equals("timestamp") && reader.peek() != JsonToken.NULL) {
          result.timestamp(reader.nextLong());
        } else if (nextName.equals("duration") && reader.peek() != JsonToken.NULL) {
          result.duration(reader.nextLong());
        } else if (nextName.equals("annotations")) {
          reader.beginArray();
          while (reader.hasNext()) {
            result.addAnnotation(ANNOTATION_ADAPTER.fromJson(reader));
          }
          reader.endArray();
        } else if (nextName.equals("binaryAnnotations")) {
          reader.beginArray();
          while (reader.hasNext()) {
            result.addBinaryAnnotation(BINARY_ANNOTATION_ADAPTER.fromJson(reader));
          }
          reader.endArray();
        } else if (nextName.equals("debug") && reader.peek() != JsonToken.NULL) {
          if (reader.nextBoolean()) result.debug(true);
        } else {
          reader.skipValue();
        }
      }
      reader.endObject();
      return result.build();
    }

    @Override public int sizeInBytes(Span value) {
      int sizeInBytes = 0;
      if (value.traceIdHigh != 0) sizeInBytes += 16;
      sizeInBytes += asciiSizeInBytes("{\"traceId\":\"") + 16; // fixed-width hex
      sizeInBytes += asciiSizeInBytes("\",\"id\":\"") + 16;
      sizeInBytes += asciiSizeInBytes("\",\"name\":\"") + jsonEscapedSizeInBytes(value.name) + 1;
      if (value.parentId != null) {
        sizeInBytes += asciiSizeInBytes(",\"parentId\":\"") + 16 + 1;
      }
      if (value.timestamp != null) {
        sizeInBytes += asciiSizeInBytes(",\"timestamp\":") + asciiSizeInBytes(value.timestamp);
      }
      if (value.duration != null) {
        sizeInBytes += asciiSizeInBytes(",\"duration\":") + asciiSizeInBytes(value.duration);
      }
      if (!value.annotations.isEmpty()) {
        sizeInBytes += asciiSizeInBytes(",\"annotations\":");
        sizeInBytes += JsonCodec.sizeInBytes(ANNOTATION_ADAPTER, value.annotations);
      }
      if (!value.binaryAnnotations.isEmpty()) {
        sizeInBytes += asciiSizeInBytes(",\"binaryAnnotations\":");
        sizeInBytes += JsonCodec.sizeInBytes(BINARY_ANNOTATION_ADAPTER, value.binaryAnnotations);
      }
      if (value.debug != null && value.debug) {
        sizeInBytes += asciiSizeInBytes(",\"debug\":true");
      }
      return ++sizeInBytes;// end curly-brace
    }

    @Override public void write(Span value, Buffer b) {
      b.writeAscii("{\"traceId\":\"");
      if (value.traceIdHigh != 0) {
        b.writeLowerHex(value.traceIdHigh);
      }
      b.writeLowerHex(value.traceId);
      b.writeAscii("\",\"id\":\"").writeLowerHex(value.id);
      b.writeAscii("\",\"name\":\"").writeJsonEscaped(value.name).writeByte('"');
      if (value.parentId != null) {
        b.writeAscii(",\"parentId\":\"").writeLowerHex(value.parentId).writeByte('"');
      }
      if (value.timestamp != null) {
        b.writeAscii(",\"timestamp\":").writeAscii(value.timestamp);
      }
      if (value.duration != null) {
        b.writeAscii(",\"duration\":").writeAscii(value.duration);
      }
      if (!value.annotations.isEmpty()) {
        b.writeAscii(",\"annotations\":");
        writeList(ANNOTATION_ADAPTER, value.annotations, b);
      }
      if (!value.binaryAnnotations.isEmpty()) {
        b.writeAscii(",\"binaryAnnotations\":");
        writeList(BINARY_ANNOTATION_ADAPTER, value.binaryAnnotations, b);
      }
      if (value.debug != null && value.debug) {
        b.writeAscii(",\"debug\":true");
      }
      b.writeByte('}');
    }

    @Override public String toString() {
      return "Span";
    }
  };

  @Override
  public Span readSpan(byte[] bytes) {
    checkArgument(bytes.length > 0, "Empty input reading Span");
    try {
      return SPAN_ADAPTER.fromJson(jsonReader(bytes));
    } catch (Exception e) {
      throw exceptionReading("Span", bytes, e);
    }
  }

  @Override public int sizeInBytes(Span value) {
    return SPAN_ADAPTER.sizeInBytes(value);
  }

  @Override
  public byte[] writeSpan(Span value) {
    return write(SPAN_ADAPTER, value);
  }

  /** Exposed for {@link Endpoint#toString()} */
  public static byte[] writeEndpoint(Endpoint value) {
    return write(ENDPOINT_ADAPTER, value);
  }

  @Override
  public List<Span> readSpans(byte[] bytes) {
    checkArgument(bytes.length > 0, "Empty input reading List<Span>");
    return readList(SPAN_ADAPTER, bytes);
  }

  @Override
  public byte[] writeSpans(List<Span> value) {
    if (value.isEmpty()) return new byte[] {'[', ']'};
    Buffer result = new Buffer(sizeInBytes(SPAN_ADAPTER, value));
    writeList(SPAN_ADAPTER, value, result);
    return result.toByteArray();
  }

  @Override
  public byte[] writeTraces(List<List<Span>> traces) {
    // Get the encoded size of the nested list so that we don't need to grow the buffer
    int sizeInBytes = overheadInBytes(traces);
    for (int i = 0; i < traces.size(); i++) {
      List<Span> spans = traces.get(i);
      sizeInBytes += overheadInBytes(spans);
      for (int j = 0; j < spans.size(); j++) {
        sizeInBytes += SPAN_ADAPTER.sizeInBytes(spans.get(j));
      }
    }

    Buffer out = new Buffer(sizeInBytes);
    out.writeByte('['); // start list of traces
    for (Iterator<List<Span>> trace = traces.iterator(); trace.hasNext(); ) {
      writeList(SPAN_ADAPTER, trace.next(), out);
      if (trace.hasNext()) out.writeByte(',');
    }
    out.writeByte(']'); // stop list of traces
    return out.toByteArray();
  }

  public List<List<Span>> readTraces(byte[] bytes) {
    JsonReader reader = jsonReader(bytes);
    List<List<Span>> result = new LinkedList<List<Span>>(); // cause we don't know how long it will be
    try {
      reader.beginArray();
      while (reader.hasNext()) {
        reader.beginArray();
        List<Span> trace = new LinkedList<Span>(); // cause we don't know how long it will be
        while (reader.hasNext()) {
          trace.add(SPAN_ADAPTER.fromJson(reader));
        }
        reader.endArray();
        result.add(trace);
      }
      reader.endArray();
      return result;
    } catch (Exception e) {
      throw exceptionReading("List<List<Span>>", bytes, e);
    }
  }

  static final JsonAdapter<DependencyLink> DEPENDENCY_LINK_ADAPTER = new JsonAdapter<DependencyLink>() {
    @Override
    public DependencyLink fromJson(JsonReader reader) throws IOException {
      DependencyLink.Builder result = DependencyLink.builder();
      reader.beginObject();
      while (reader.hasNext()) {
        String nextName = reader.nextName();
        if (nextName.equals("parent")) {
          result.parent(reader.nextString());
        } else if (nextName.equals("child")) {
          result.child(reader.nextString());
        } else if (nextName.equals("callCount")) {
          result.callCount(reader.nextLong());
        } else {
          reader.skipValue();
        }
      }
      reader.endObject();
      return result.build();
    }

    @Override public int sizeInBytes(DependencyLink value) {
      int sizeInBytes = 0;
      sizeInBytes += asciiSizeInBytes("{\"parent\":\"") + jsonEscapedSizeInBytes(value.parent);
      sizeInBytes += asciiSizeInBytes("\",\"child\":\"") + jsonEscapedSizeInBytes(value.child);
      sizeInBytes += asciiSizeInBytes("\",\"callCount\":") + asciiSizeInBytes(value.callCount);
      return ++sizeInBytes;// end curly-brace
    }

    @Override public void write(DependencyLink value, Buffer b) {
      b.writeAscii("{\"parent\":\"").writeJsonEscaped(value.parent);
      b.writeAscii("\",\"child\":\"").writeJsonEscaped(value.child);
      b.writeAscii("\",\"callCount\":").writeAscii(value.callCount).writeByte('}');
    }

    @Override public String toString() {
      return "DependencyLink";
    }
  };

  @Override
  public DependencyLink readDependencyLink(byte[] bytes) {
    checkArgument(bytes.length > 0, "Empty input reading DependencyLink");
    try {
      return DEPENDENCY_LINK_ADAPTER.fromJson(jsonReader(bytes));
    } catch (Exception e) {
      throw exceptionReading("Span", bytes, e);
    }
  }

  @Override
  public byte[] writeDependencyLink(DependencyLink value) {
    return write(DEPENDENCY_LINK_ADAPTER, value);
  }

  @Override
  public List<DependencyLink> readDependencyLinks(byte[] bytes) {
    checkArgument(bytes.length > 0, "Empty input reading List<DependencyLink>");
    return readList(DEPENDENCY_LINK_ADAPTER, bytes);
  }

  @Override
  public byte[] writeDependencyLinks(List<DependencyLink> value) {
    Buffer result = new Buffer(sizeInBytes(DEPENDENCY_LINK_ADAPTER, value));
    writeList(DEPENDENCY_LINK_ADAPTER, value, result);
    return result.toByteArray();
  }

  static final JsonAdapter<String> STRING_ADAPTER = new JsonAdapter<String>() {

    @Override
    public String fromJson(JsonReader reader) throws IOException {
      return reader.nextString();
    }

    @Override public int sizeInBytes(String value) {
      return jsonEscapedSizeInBytes(value) + 2; // For quotes
    }

    @Override public void write(String value, Buffer buffer) {
      buffer.writeByte('"').writeJsonEscaped(value).writeByte('"');
    }
  };

  public List<String> readStrings(byte[] bytes) {
    checkArgument(bytes.length > 0, "Empty input reading List<String>");
    return readList(STRING_ADAPTER, bytes);
  }

  public byte[] writeStrings(List<String> value) {
    Buffer result = new Buffer(sizeInBytes(STRING_ADAPTER, value));
    writeList(STRING_ADAPTER, value, result);
    return result.toByteArray();
  }

  static <T> List<T> readList(JsonAdapter<T> adapter, byte[] bytes) {
    JsonReader reader = jsonReader(bytes);
    List<T> result;
    try {
      reader.beginArray();
      if (reader.hasNext()) {
        result = new LinkedList<T>(); // cause we don't know how long it will be
      } else {
        result = Collections.emptyList();
      }
      while (reader.hasNext()) {
        result.add(adapter.fromJson(reader));
      }
      reader.endArray();
      return result;
    } catch (Exception e) {
      throw exceptionReading("List<" + adapter + ">", bytes, e);
    }
  }

  private static JsonReader jsonReader(byte[] bytes) {
    return new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes)));
  }

  /** Inability to encode is a programming bug. */
  static <T> byte[] write(Buffer.Writer<T> writer, T value) {
    Buffer b = new Buffer(writer.sizeInBytes(value));
    try {
      writer.write(value, b);
    } catch (RuntimeException e) {
      throw assertionError("Could not write " + value + " as json", e);
    }
    return b.toByteArray();
  }

  static <T> int sizeInBytes(Buffer.Writer<T> writer, List<T> value) {
    int sizeInBytes = overheadInBytes(value);
    for (int i = 0, length = value.size(); i < length; i++) {
      sizeInBytes += writer.sizeInBytes(value.get(i));
    }
    return sizeInBytes;
  }

  static <T> int overheadInBytes(List<T> value) {
    int sizeInBytes = 2; // brackets
    if (value.size() > 1) sizeInBytes += value.size() - 1; // comma to join elements
    return sizeInBytes;
  }

  static <T> void writeList(Buffer.Writer<T> writer, List<T> value, Buffer b) {
    b.writeByte('[');
    for (int i = 0, length = value.size(); i < length; ) {
      writer.write(value.get(i++), b);
      if (i < length) b.writeByte(',');
    }
    b.writeByte(']');
  }

  static IllegalArgumentException exceptionReading(String type, byte[] bytes, Exception e) {
    String cause = e.getMessage() == null ? "Error" : e.getMessage();
    if (cause.indexOf("malformed") != -1) cause = "Malformed";
    String message = String.format("%s reading %s from json: %s", cause, type, new String(bytes, UTF_8));
    throw new IllegalArgumentException(message, e);
  }

  static abstract class JsonAdapter<T> implements Buffer.Writer<T> {
    abstract T fromJson(JsonReader reader) throws IOException;
  }
}
